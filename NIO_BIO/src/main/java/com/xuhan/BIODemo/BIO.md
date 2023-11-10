# BIO到NIO源码的一些事儿之BIO

此篇文章会详细解读由BIO到NIO的逐步演进的心灵路程，为Reactor-Netty 库的讲解铺平道路。

关于`Java编程方法论-Reactor与Webflux`的视频分享，已经完成了Rxjava 与 Reactor，b站地址如下:

Rxjava源码解读与分享：[www.bilibili.com/video/av345…](https://link.juejin.cn?target=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2Fav34537840)

Reactor源码解读与分享：[www.bilibili.com/video/av353…](https://link.juejin.cn?target=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2Fav35326911)

其它大佬的分享：https://mdnice.com/writing/038282d3d7084f3ab2bfe651ca4ee8db

## 引入

我们通过一个BIO的Demo来展示其用法:

```java
//服务端
public class BIOServer {
    public void initBIOServer(int port)
    {
        ServerSocket serverSocket = null;//服务端Socket
        Socket socket = null;//客户端socket
        BufferedReader reader = null;
        String inputContent;
        int count = 0;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println(stringNowTime() + ": serverSocket started");
            while(true)
            {
                socket = serverSocket.accept();
                System.out.println(stringNowTime() + ": id为" + socket.hashCode()+ "的Clientsocket connected");
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while ((inputContent = reader.readLine()) != null) {
                    System.out.println("收到id为" + socket.hashCode() + "  "+inputContent);
                    count++;
                }
                System.out.println("id为" + socket.hashCode()+ "的Clientsocket "+stringNowTime()+"读取结束");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            try {
                reader.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public String stringNowTime()
    {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.format(new Date());
    }

    public static void main(String[] args) {
        BIOServer server = new BIOServer();
        server.initBIOServer(8888);

    }
}
// 客户端
public class BIOClient {

    public void initBIOClient(String host, int port) {
        BufferedReader reader = null;
        BufferedWriter writer = null;
        Socket socket = null;
        String inputContent;
        int count = 0;
        try {
            reader = new BufferedReader(new InputStreamReader(System.in));
            socket = new Socket(host, port);
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            System.out.println("clientSocket started: " + stringNowTime());
            while (((inputContent = reader.readLine()) != null) && count < 2) {
                inputContent = stringNowTime() + ": 第" + count + "条消息: " + inputContent + "\n";
                writer.write(inputContent);//将消息发送给服务端
                writer.flush();
                count++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
                reader.close();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String stringNowTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.format(new Date());
    }

    public static void main(String[] args) {
        BIOClient client = new BIOClient();
        client.initBIOClient("127.0.0.1", 8888);
    }

}


```

通过上面的例子，我们可以知道，无论是服务端还是客户端，我们关注的几个操作有基于服务端的`serverSocket = new ServerSocket(port)` `serverSocket.accept()`，基于客户端的`Socket socket = new Socket(host, port);` 以及两者都有的读取与写入Socket数据的方式，即通过流来进行读写，这个读写不免通过一个中间字节数组buffer来进行。

## ServerSocket中bind解读

于是，我们通过源码来看这些相应的逻辑。我们先来看`ServerSocket.java`这个类的相关代码。 我们查看`ServerSocket.java`的构造器可以知道，其最后依然会调用它的`bind`方法

```java
//java.net.ServerSocket#ServerSocket(int)
public ServerSocket(int port) throws IOException {
  	//
    this(port, 50, null);
}

public ServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
  //注意这边有个setImpl 会new一个SocksSocketImpl
    setImpl();
    if (port < 0 || port > 0xFFFF)
        throw new IllegalArgumentException(
                    "Port value out of range: " + port);
    if (backlog < 1)
        backlog = 50;
    try {
      	//backlog大小为50
        bind(new InetSocketAddress(bindAddr, port), backlog);
    } catch(SecurityException e) {
        close();
        throw e;
    } catch(IOException e) {
        close();
        throw e;
    }
}

```

​    按照我们的Demo和上面的源码可知，这里传入的参数endpoint并不会为null，同时，属于`InetSocketAddress`类型，backlog大小为50，于是，我们应该关注的主要代码逻辑也就是`getImpl().bind(epoint.getAddress(), epoint.getPort());`:

```java
    public void bind(SocketAddress endpoint, int backlog) throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        if (!oldImpl && isBound())
            throw new SocketException("Already bound");
        if (endpoint == null)
            endpoint = new InetSocketAddress(0);
        if (!(endpoint instanceof InetSocketAddress))
            throw new IllegalArgumentException("Unsupported address type");
        InetSocketAddress epoint = (InetSocketAddress) endpoint;
        if (epoint.isUnresolved())
            throw new SocketException("Unresolved address");
        if (backlog < 1)
          backlog = 50;
        try {
            SecurityManager security = System.getSecurityManager();
            if (security != null)
                security.checkListen(epoint.getPort());
          	//应该关注的主要代码逻辑
            getImpl().bind(epoint.getAddress(), epoint.getPort());
            getImpl().listen(backlog);
            bound = true;
        } catch(SecurityException e) {
            bound = false;
            throw e;
        } catch(IOException e) {
            bound = false;
            throw e;
        }
    }

```

  这里`getImpl()`，由上面构造器的实现中，我们有看到`setImpl();`，可知，其`factory`默认为null，所以，这里我们关注的是`SocksSocketImpl`这个类，创建其对象，并将当前`ServerSocket`对象设定其中，这个设定的源码请在`SocksSocketImpl`的父类`java.net.SocketImpl`中查看。 

​    那么getImpl也就明了了，其实就是我们Socket的底层实现对应的实体类了，因为不同的操作系统内核是不同的，他们对于Socket的实现当然会各有不同，我们这点要注意下，这里针对的是win下面的系统。

```java
/**
* The factory for all server sockets.
*/
private static SocketImplFactory factory = null;
private void setImpl() {
    if (factory != null) {
        impl = factory.createSocketImpl();
        checkOldImpl();
    } else {
        // No need to do a checkOldImpl() here, we know it's an up to date
        // SocketImpl!
      	// 开始我们的factory=null 所以这边new一个SocksSocketImpl
        impl = new SocksSocketImpl();
    }
    if (impl != null)
      	// new完impl之后，并将当前`ServerSocket`对象设定其中
        impl.setServerSocket(this);
}
/**
* Get the {@code SocketImpl} attached to this socket, creating
* it if necessary.
*
* @return  the {@code SocketImpl} attached to that ServerSocket.
* @throws SocketException if creation fails.
* @since 1.4
*/
SocketImpl getImpl() throws SocketException {
    if (!created)
        createImpl();
    return impl;
}
/**
* Creates the socket implementation.
*
* @throws IOException if creation fails
* @since 1.4
*/
void createImpl() throws SocketException {
    if (impl == null)
        setImpl();
    try {
      	//之前代码setImpl已经创建过了，这边其实就更改了标志位
        impl.create(true);
        created = true;
    } catch (IOException e) {
        throw new SocketException(e.getMessage());
    }
}

```

我们再看`SocksSocketImpl`的bind方法实现，然后得到其最后无非是调用本地方法`bind0`（win版本，因为socket是根据操作系统底层的通讯协议进行的包装，所以不同系统会不太一样）。

```java
//java.net.AbstractPlainSocketImpl#bind
/**
* Binds the socket to the specified address of the specified local port.
* @param address the address
* @param lport the port
*/
protected synchronized void bind(InetAddress address, int lport)
    throws IOException
{
    synchronized (fdLock) {
        if (!closePending && (socket == null || !socket.isBound())) {
            NetHooks.beforeTcpBind(fd, address, lport);
        }
    }
    socketBind(address, lport);
    if (socket != null)
        socket.setBound();
    if (serverSocket != null)
        serverSocket.setBound();
}


//win版本 
//java.net.PlainSocketImpl#socketBind
@Override
void socketBind(InetAddress address, int port) throws IOException {
    int nativefd = checkAndReturnNativeFD();

    if (address == null)
        throw new NullPointerException("inet address argument is null.");

    if (preferIPv4Stack && !(address instanceof Inet4Address))
        throw new SocketException("Protocol family not supported");

    bind0(nativefd, address, port, useExclusiveBind);
    if (port == 0) {
        localport = localPort0(nativefd);
    } else {
        localport = port;
    }

    this.address = address;
}
//java.net.PlainSocketImpl#bind0
static native void bind0(int fd, InetAddress localAddress, int localport,
                             boolean exclBind)
        throws IOException;



//mac版本
//java.net.PlainSocketImpl#socketBind
native void socketBind(InetAddress address, int port)
        throws IOException;
```

   这里，我们还要了解的是，使用了多线程只是能够实现对"业务逻辑处理"的多线程，但是对于数据报文的接收还是需要一个一个来的，也就是我们上面Demo中见到的accept以及read方法阻塞问题，多线程是根本解决不了的。

​	那么首先我们来看看accept为什么会造成阻塞，accept方法的作用是询问操作系统是否有新的Socket套接字信息从端口XXX处发送过来，注意这里询问的是操作系统，也就是说Socket套接字IO模式的支持是基于操作系统的，如果操作系统没有发现有套接字从指定端口XXX连接进来，那么操作系统就会等待，这样accept方法就会阻塞，他的内部实现使用的是操作系统级别的同步IO。（有来才有往）

## ServerSocket中accept解读

​	于是，我们来分析下`ServerSocket.accept`方法的源码过程:

```java
public Socket accept() throws IOException {
    if (isClosed())
        throw new SocketException("Socket is closed");
    if (!isBound())
        throw new SocketException("Socket is not bound yet");
    Socket s = new Socket((SocketImpl) null);
    implAccept(s);
    return s;
}

```

首先进行的是一些判断，接着创建了一个Socket对象（为什么这里要创建一个Socket对象，后面会讲到），执行了implAccept方法，来看看implAccept方法：

```java
/**
* Subclasses of ServerSocket use this method to override accept()
* to return their own subclass of socket.  So a FooServerSocket
* will typically hand this method an <i>empty</i> FooSocket.  On
* return from implAccept the FooSocket will be connected to a client.
*
* @param s the Socket
* @throws java.nio.channels.IllegalBlockingModeException
*         if this socket has an associated channel,
*         and the channel is in non-blocking mode
* @throws IOException if an I/O error occurs when waiting
* for a connection.
* @since   1.1
* @revised 1.4
* @spec JSR-51
	 1.accept函数返回的新socket其实指代的是本次创建的连接，而一个连接是包括两部分信息的，一个是源IP和源端口，另一个是宿IP和宿端口。所以，accept可以产生多个不同的socket，而这些socket里包含的宿IP和宿端口是不变的，变化的只是源IP和源端口。
	 2.个人认为整个accept() 操作比较”恶心“（个人观点）的是几个引用的赋值变化上面，暂时”解绑“的目的是在进行底层Socket连接的时候，如果Socket出现异常也没有影响，此时Socket持有的引用也是null，可以无阻碍的重新进行下一次Socket连接。
	换句话说，整个Socket要么对接成功，要么就是重置回没对接之前的状态可以进行下一次尝试，保证ServerSocket会收到一个没有任何异常的Socket连接。
*/
protected final void implAccept(Socket s) throws IOException {
SocketImpl si = null;
try {
    if (s.impl == null)
        s.setImpl();
    else {
        s.impl.reset();
    }
    si = s.impl;
    s.impl = null;
  	//这边初始化地址和文件描述符，但其实都是空的
    si.address = new InetAddress();
    si.fd = new FileDescriptor();
  	//这边去设定来源ip和来源文件描述符 文件描述符：描述连接状态 0 in 1 out 2 error -1什么都无这种
    getImpl().accept(si);  // <1>
    SocketCleanable.register(si.fd);   // raw fd has been set

    SecurityManager security = System.getSecurityManager();
    if (security != null) {
        security.checkAccept(si.getInetAddress().getHostAddress(),
                                si.getPort());
    }
} catch (IOException e) {
    if (si != null)
        si.reset();
    s.impl = si;
    throw e;
} catch (SecurityException e) {
    if (si != null)
        si.reset();
    s.impl = si;
    throw e;
}
s.impl = si;
s.postAccept();
}

```

上面执行了<1>处getImpl的accept方法之后，我们在AbstractPlainSocketImpl找到accept方法：

```java
//java.net.AbstractPlainSocketImpl#accept
/**
* Accepts connections.
* @param s the connection
*/
protected void accept(SocketImpl s) throws IOException {
  acquireFD();
  try {
      socketAccept(s);
  } finally {
      releaseFD();
  }
}

```

可以看到他调用了socketAccept方法，因为每个操作系统的Socket地实现都不同，所以这里Windows下就执行了我们PlainSocketImpl里面的socketAccept方法:

```java
// java.net.PlainSocketImpl#socketAccept
@Override
void socketAccept(SocketImpl s) throws IOException {
    int nativefd = checkAndReturnNativeFD();

    if (s == null)
        throw new NullPointerException("socket is null");

    int newfd = -1;
    InetSocketAddress[] isaa = new InetSocketAddress[1];
    if (timeout <= 0) {  //<1>
        newfd = accept0(nativefd, isaa); // <2>
    } else {
        configureBlocking(nativefd, false);
        try {
            waitForNewConnection(nativefd, timeout);
            newfd = accept0(nativefd, isaa);  // <3>
            if (newfd != -1) {
                configureBlocking(newfd, true);
            }
        } finally {
            configureBlocking(nativefd, true);
        }
    } // <4>
    /* Update (SocketImpl)s' fd */
    fdAccess.set(s.fd, newfd);
    /* Update socketImpls remote port, address and localport */
    InetSocketAddress isa = isaa[0];
    s.port = isa.getPort();
    s.address = isa.getAddress();
    s.localport = localport;
    if (preferIPv4Stack && !(s.address instanceof Inet4Address))
        throw new SocketException("Protocol family not supported");
}
//java.net.PlainSocketImpl#accept0
 static native int accept0(int fd, InetSocketAddress[] isaa) throws IOException;

```

mac linux版本：直接进native方法了

```
 native void socketAccept(SocketImpl s) throws IOException;
```

​    这里<1>到<4>之间是我们关注的代码，<2>和<3>执行了accept0方法，这个是native方法，具体来说就是与操作系统交互来实现监听指定端口上是否有客户端接入，正是因为accept0在没有客户端接入的时候会一直处于阻塞状态，所以造成了我们程序级别的accept方法阻塞。当然对于程序级别的阻塞，我们是可以避免的，也就是我们可以将accept方法修改成非阻塞式，但是对于accept0造成的阻塞我们暂时是没法改变的，操作系统级别的阻塞其实就是我们通常所说的同步异步中的同步了。 

​	前面说到我们可以在程序级别改变accept的阻塞，具体怎么实现？其实就是通过我们上面socketAccept方法中判断timeout的值来实现，在第<1>处判断timeout的值如果小于等于0，那么直接执行accept0方法，这时候将一直处于阻塞状态，但是如果我们设置了timeout的话，即timeout值大于0的话，则程序会在等到我们设置的时间后返回，注意这里的newfd如果等于-1的话，表示这次accept没有发现有数据从底层返回；那么到底timeout的值是在哪设置？我们可以通过ServerSocket的setSoTimeout方法进行设置，来看看这个方法：

```java
// java.net.ServerSocket#setSoTimeout
/**
* Enable/disable {@link SocketOptions#SO_TIMEOUT SO_TIMEOUT} with the
* specified timeout, in milliseconds.  With this option set to a non-zero
* timeout, a call to accept() for this ServerSocket
* will block for only this amount of time.  If the timeout expires,
* a <B>java.net.SocketTimeoutException</B> is raised, though the
* ServerSocket is still valid.  The option <B>must</B> be enabled
* prior to entering the blocking operation to have effect.  The
* timeout must be {@code > 0}.
* A timeout of zero is interpreted as an infinite timeout.
* @param timeout the specified timeout, in milliseconds
* @exception SocketException if there is an error in
* the underlying protocol, such as a TCP error.
* @since   1.1
* @see #getSoTimeout()
*/
public synchronized void setSoTimeout(int timeout) throws SocketException {
  if (isClosed())
      throw new SocketException("Socket is closed");
  getImpl().setOption(SocketOptions.SO_TIMEOUT, timeout);
}

```

其执行了getImpl的setOption方法，并且设置了timeout时间，这里，我们从AbstractPlainSocketImpl中查看：

```java
//java.net.AbstractPlainSocketImpl#setOption
public void setOption(int opt, Object val) throws SocketException {
    if (isClosedOrPending()) {
        throw new SocketException("Socket Closed");
    }
    boolean on = true;
    switch (opt) {
        /* check type safety b4 going native.  These should never
            * fail, since only java.Socket* has access to
            * PlainSocketImpl.setOption().
            */
    case SO_LINGER:
        if (val == null || (!(val instanceof Integer) && !(val instanceof Boolean)))
            throw new SocketException("Bad parameter for option");
        if (val instanceof Boolean) {
            /* true only if disabling - enabling should be Integer */
            on = false;
        }
        break;
    case SO_TIMEOUT: //<1>
        if (val == null || (!(val instanceof Integer)))
            throw new SocketException("Bad parameter for SO_TIMEOUT");
        int tmp = ((Integer) val).intValue();
        if (tmp < 0)
            throw new IllegalArgumentException("timeout < 0");
        timeout = tmp;
        break;
    case IP_TOS:
            if (val == null || !(val instanceof Integer)) {
                throw new SocketException("bad argument for IP_TOS");
            }
            trafficClass = ((Integer)val).intValue();
            break;
    case SO_BINDADDR:
        throw new SocketException("Cannot re-bind socket");
    case TCP_NODELAY:
        if (val == null || !(val instanceof Boolean))
            throw new SocketException("bad parameter for TCP_NODELAY");
        on = ((Boolean)val).booleanValue();
        break;
    case SO_SNDBUF:
    case SO_RCVBUF:
        if (val == null || !(val instanceof Integer) ||
            !(((Integer)val).intValue() > 0)) {
            throw new SocketException("bad parameter for SO_SNDBUF " +
                                        "or SO_RCVBUF");
        }
        break;
    case SO_KEEPALIVE:
        if (val == null || !(val instanceof Boolean))
            throw new SocketException("bad parameter for SO_KEEPALIVE");
        on = ((Boolean)val).booleanValue();
        break;
    case SO_OOBINLINE:
        if (val == null || !(val instanceof Boolean))
            throw new SocketException("bad parameter for SO_OOBINLINE");
        on = ((Boolean)val).booleanValue();
        break;
    case SO_REUSEADDR:
        if (val == null || !(val instanceof Boolean))
            throw new SocketException("bad parameter for SO_REUSEADDR");
        on = ((Boolean)val).booleanValue();
        break;
    case SO_REUSEPORT:
        if (val == null || !(val instanceof Boolean))
            throw new SocketException("bad parameter for SO_REUSEPORT");
        if (!supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT))
            throw new UnsupportedOperationException("unsupported option");
        on = ((Boolean)val).booleanValue();
        break;
    default:
        throw new SocketException("unrecognized TCP option: " + opt);
    }
    socketSetOption(opt, on, val);
}

```

这个方法比较长，我们仅看与`timeout`有关的代码，即<1>处的代码。其实这里仅仅就是将我们setOption里面传入的timeout值设置到了AbstractPlainSocketImpl的全局变量timeout里而已。

这样，我们就可以在程序级别将accept方法设置成为非阻塞式的了，但是read方法现在还是阻塞式的，即后面我们还需要改造read方法，同样将它在程序级别上变成非阻塞式。

原理可见：https://blog.csdn.net/weixin_43710268/article/details/109712344

​		当调用socket.getInputStream().read()方法时,由于这个read()方法是阻塞的,read()方法会一直处于阻塞状态等待接受数据而导致不能往下执行代码;而setSoTimeout()方法就是设置阻塞的超时时间。当设置了超时时间后,如果read()方法读不到数据,处于等待读取数据的状态时,就会开始计算超时时间，当到达超时时间还没有新的数据可以读取的时候,read()方法就会抛出io异常,结束read()方法的阻塞状态;如果到达超时时间前,从缓冲区读取到了数据,那么就重新计算超时时间。

![image-20231109155341733](/Users/xuhan/program/program/workLearning/Learn_NIO_BIO/NIO_BIO/src/main/java/com/xuhan/BIODemo/BIO.assets/image-20231109155341733.png)

## 通过Demo改造来进行accept的非阻塞实现

在正式改造前，我们有必要来解释下Socket下同步/异步和阻塞/非阻塞:

**同步/异步**是属于操作系统级别的，指的是操作系统在收到程序请求的IO之后，如果IO资源没有准备好的话，该如何响应程序的问题，同步的话就是不响应，直到IO资源准备好；而异步的话则会返回给程序一个标志，这个标志用于当IO资源准备好后通过事件机制发送的内容应该发到什么地方。

阻塞/非阻塞是属于程序级别的，指的是程序在请求操作系统进行IO操作时，如果IO资源没有准备好的话，程序该怎么处理的问题，阻塞的话就是程序什么都不做，一直等到IO资源准备好，非阻塞的话程序则继续运行，但是会时不时的去查看下IO到底准备好没有呢；

我们通常见到的BIO是同步阻塞式的，同步的话说明操作系统底层是一直等待IO资源准备直到ok的，阻塞的话是程序本身也在一直等待IO资源准备直到ok，具体来讲程序级别的阻塞就是accept和read造成的，我们可以通过改造将其变成非阻塞式，但是操作系统层次的阻塞我们没法改变。

我们的NIO是同步非阻塞式的，其实它的非阻塞实现原理和我们上面的讲解差不多的，就是为了改善accept和read方法带来的阻塞现象，所以引入了`Channel`和`Buffer`的概念。 好了，我们对我们的Demo进行改进，解决accept带来的阻塞问题(为多个客户端连接做的异步处理，这里就不多解释了，读者可自行思考，实在不行可到知秋相关视频中找到对应解读)：

```java
public class BIOProNotB {

    public void initBIOServer(int port) {
        ServerSocket serverSocket = null;//服务端Socket
        Socket socket = null;//客户端socket
        ExecutorService threadPool = Executors.newCachedThreadPool();
        ClientSocketThread thread = null;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(1000);
            System.out.println(stringNowTime() + ": serverSocket started");
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (SocketTimeoutException e) {
                    //运行到这里表示本次accept是没有收到任何数据的，服务端的主线程在这里可以做一些其他事情
                    System.out.println("now time is: " + stringNowTime());
                    continue;
                }
                System.out.println(stringNowTime() + ": id为" + socket.hashCode() + "的Clientsocket connected");
                thread = new ClientSocketThread(socket);
                threadPool.execute(thread);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String stringNowTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        return format.format(new Date());
    }

    class ClientSocketThread extends Thread {
        public Socket socket;

        public ClientSocketThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            BufferedReader reader = null;
            String inputContent;
            int count = 0;
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while ((inputContent = reader.readLine()) != null) {
                    System.out.println("收到id为" + socket.hashCode() + "  " + inputContent);
                    count++;
                }
                System.out.println("id为" + socket.hashCode() + "的Clientsocket " + stringNowTime() + "读取结束");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    reader.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        BIOProNotB server = new BIOProNotB();
        server.initBIOServer(8888);
    }


}

```

​	因为我们的ServerSocket设置了timeout时间，这样的话调用accept方法的时候每隔1s他就会被唤醒一次，而不再是一直在那里，只有有客户端接入才会返回信息；我们运行一下看看结果：

```
2019-01-02 17:28:43:362: serverSocket started
now time is: 2019-01-02 17:28:44:363
now time is: 2019-01-02 17:28:45:363
now time is: 2019-01-02 17:28:46:363
now time is: 2019-01-02 17:28:47:363
now time is: 2019-01-02 17:28:48:363
now time is: 2019-01-02 17:28:49:363
now time is: 2019-01-02 17:28:50:363
now time is: 2019-01-02 17:28:51:364
now time is: 2019-01-02 17:28:52:365
now time is: 2019-01-02 17:28:53:365
now time is: 2019-01-02 17:28:54:365
now time is: 2019-01-02 17:28:55:365
now time is: 2019-01-02 17:28:56:365 // <1>
2019-01-02 17:28:56:911: id为1308927845的Clientsocket connected
now time is: 2019-01-02 17:28:57:913 // <2>
now time is: 2019-01-02 17:28:58:913

```

可以看到，我们刚开始并没有客户端接入的时候，是会执行`System.out.println("now time is: " + stringNowTime());`的输出，还有一点需要注意的就是，仔细看看上面的输出结果的标记<1>与<2>，你会发现<2>处时间值不是17:28:57:365，原因就在于如果accept正常返回值的话，是不会执行catch语句部分的。

## 通过Demo改造来进行read的非阻塞实现

