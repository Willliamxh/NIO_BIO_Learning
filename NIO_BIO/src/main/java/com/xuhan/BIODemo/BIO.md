# BIO到NIO源码的一些事儿之BIO

此篇文章会详细解读由BIO到NIO的逐步演进的心灵路程，为Reactor-Netty 库的讲解铺平道路。

关于`Java编程方法论-Reactor与Webflux`的视频分享，已经完成了Rxjava 与 Reactor，b站地址如下:

Rxjava源码解读与分享：[www.bilibili.com/video/av345…](https://link.juejin.cn?target=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2Fav34537840)

Reactor源码解读与分享：[www.bilibili.com/video/av353…](https://link.juejin.cn?target=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2Fav35326911)

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

​	那么首先我们来看看accept为什么会造成阻塞，accept方法的作用是询问操作系统是否有新的Socket套接字信息从端口XXX处发送过来，注意这里询问的是操作系统，也就是说Socket套接字IO模式的支持是基于操作系统的，如果操作系统没有发现有套接字从指定端口XXX连接进来，那么操作系统就会等待，这样accept方法就会阻塞，他的内部实现使用的是操作系统级别的同步IO。

## ServerSocket中accept解读

​	于是，我们来分析下`ServerSocket.accept`方法的源码过程:



