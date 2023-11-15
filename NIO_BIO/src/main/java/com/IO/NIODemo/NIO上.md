# BIO到NIO源码的一些事儿之NIO 上

## 前言

此篇文章会详细解读NIO的功能逐步丰满的路程，为Reactor-Netty 库的讲解铺平道路。

关于Java编程方法论-Reactor与Webflux的视频分享，已经完成了Rxjava 与 Reactor，b站地址如下:

Rxjava源码解读与分享：[www.bilibili.com/video/av345…](https://link.juejin.cn?target=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2Fav34537840)

Reactor源码解读与分享：[www.bilibili.com/video/av353…](https://link.juejin.cn?target=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2Fav35326911)

闪电侠学netty：https://www.jianshu.com/p/a4e03835921a

闪电侠笔记：https://juejin.cn/post/7245240119090298936?searchId=20231110142428D73B1CB2E1D78613840F#heading-14



## 场景代入

​	接上一篇 [BIO到NIO源码的一些事儿之BIO](https://juejin.cn/post/6844903751178780686)，我们来接触NIO的一些事儿。

​	在上一篇中，我们可以看到，我们要做到异步非阻塞，我们自己进行的是创建线程池同时对部分代码做timeout的修改来对接客户端，但是弊端也很清晰，我们转换下思维，这里举个场景例子，A班同学要和B班同学一起一对一完成任务，每对人拿到的任务是不一样的，消耗的时间有长有短，任务因为有奖励所以同学们会抢，传统模式下，A班同学和B班同学不经管理话，即便只是一个心跳检测的任务都得一起，在这种情况下，客户端根本不会有数据要发送，只是想告诉服务器自己还活着，这种情况下，假如B班再来一个同学做对接的话，就很有问题了，B班的每一个同学都可以看成服务器端的一个线程。所以，我们需要一个管理者，于是`Selector`就出现了，作为管理者，这里，我们往往需要管理同学们的状态，是否在等待任务，是否在接收信息，是否在输出信息等等，`Selector`更侧重于动作，针对于这些状态标签来做事情就可以了，那这些状态标签其实也是需要管理的，于是`SelectionKey`也就应运而生。接着我们需要对这些同学进行包装增强，使之携带这样的标签。同样，对于同学我们应该进一步解放双手的，比如给其配台电脑，这样，同学是不是可以做更多的事情了，那这个电脑在此处就是Buffer的存在了。 于是在NIO中最主要是有三种角色的，`Buffer`缓冲区，`Channel`通道，`Selector`选择器，我们都涉及到了，接下来，我们对其源码一步步分析解读。

​	补充一下NIO的理解（来自https://www.jianshu.com/p/a4e03835921a）：

​	NIO编程模型中，新来一个连接不再创建一个新的线程，而是可以把这条连接直接绑定到某个固定的线程，然后这条连接所有的读写都由这个线程来负责，那么他是怎么做到的？我们用一幅图来对比一下IO与NIO：

![img](/Users/xuhan/program/program/workLearning/Learn_NIO_BIO/NIO_BIO/src/main/java/com/xuhan/NIODemo/NIO上.assets/webp)

 	如上图所示，IO模型中，一个连接来了，会创建一个线程，对应一个while死循环，死循环的目的就是不断监测这条连接上是否有数据可以读，大多数情况下，1w个连接里面同一时刻只有少量的连接有数据可读，因此，很多个while死循环都白白浪费掉了，因为读不出啥数据。

​	而在NIO模型中，他把这么多while死循环变成一个死循环，这个死循环由一个线程控制，那么他又是如何做到一个线程，一个while死循环就能监测1w个连接是否有数据可读的呢？
 这就是NIO模型中selector的作用，一条连接来了之后，现在不创建一个while死循环去监听是否有数据可读了，而是直接把这条连接注册到selector上，然后，通过检查这个selector，就可以批量监测出有数据可读的连接，进而读取数据，下面我再举个非常简单的生活中的例子说明IO与NIO的区别。

​	在一家幼儿园里，小朋友有上厕所的需求，小朋友都太小以至于你要问他要不要上厕所，他才会告诉你。幼儿园一共有100个小朋友，有两种方案可以解决小朋友上厕所的问题：

1. 每个小朋友配一个老师。每个老师隔段时间询问小朋友是否要上厕所，如果要上，就领他去厕所，100个小朋友就需要100个老师来询问，并且每个小朋友上厕所的时候都需要一个老师领着他去上，这就是IO模型，一个连接对应一个线程。
2. 所有的小朋友都配同一个老师。这个老师隔段时间询问所有的小朋友是否有人要上厕所，然后每一时刻把所有要上厕所的小朋友批量领到厕所，这就是NIO模型，所有小朋友都注册到同一个老师，对应的就是所有的连接都注册到一个线程，然后批量轮询。

这就是NIO模型解决线程资源受限的方案，实际开发过程中，我们会开多个线程，每个线程都管理着一批连接，相对于IO模型中一个线程管理一条连接，消耗的线程资源大幅减少

### NIO对比BIO

**BIO线程切换效率低下**

由于NIO模型中线程数量大大降低，线程切换效率因此也大幅度提高

**BIO读写以字节为单位**

NIO解决这个问题的方式是数据读写不再以字节为单位，而是以字节块为单位。IO模型中，每次都是从操作系统底层一个字节一个字节地读取数据，而NIO维护一个缓冲区，每次可以从这个缓冲区里面读取一块的数据，
 这就好比一盘美味的豆子放在你面前，你用筷子一个个夹（每次一个），肯定不如要勺子挖着吃（每次一批）效率来得高。



## Channel解读

### 赋予Channel可异步可中断的能力

有上可知，同学其实都是代表着一个个的`Socket`的存在，那么这里`Channel`就是对其进行的增强包装，也就是`Channel`的具体实现里应该有`Socket`这个字段才行，然后具体实现类里面也是紧紧围绕着`Socket`具备的功能来做文章的。那么，我们首先来看`java.nio.channels.Channel`接口的设定:

```java
public interface Channel extends Closeable {

    /**
     * Tells whether or not this channel is open.
     *
     * @return {@code true} if, and only if, this channel is open
     */
    public boolean isOpen();

    /**
     * Closes this channel.
     *
     * <p> After a channel is closed, any further attempt to invoke I/O
     * operations upon it will cause a {@link ClosedChannelException} to be
     * thrown.
     *
     * <p> If this channel is already closed then invoking this method has no
     * effect.
     *
     * <p> This method may be invoked at any time.  If some other thread has
     * already invoked it, however, then another invocation will block until
     * the first invocation is complete, after which it will return without
     * effect. </p>
     *
     * @throws  IOException  If an I/O error occurs
     */
    public void close() throws IOException;

}
```

​	此处就是很直接的设定，判断Channel是否是open状态，关闭Channel的动作，我们在接下来会讲到`ClosedChannelException`是如何具体在代码中发生的。

  有时候，一个Channel可能会被异步关闭和中断，这也是我们所需求的。

​	那么要实现这个效果我们须得设定一个可以进行此操作效果的接口。达到的具体的效果应该是如果线程在实现这个接口的的Channel中进行IO操作的时候，另一个线程可以调用该Channel的close方法。导致的结果就是，进行IO操作的那个阻塞线程会收到一个`AsynchronousCloseException`异常。

​	同样，我们应该考虑到另一种情况，如果线程在实现这个接口的的Channel中进行IO操作的时候，另一个线程可能会调用被阻塞线程的`interrupt`方法(`Thread#interrupt()`)，从而导致Channel关闭，那么这个阻塞的线程应该要收到`ClosedByInterruptException`异常，同时将中断状态设定到该阻塞线程之上。

​	这时候，如果中断状态已经在该线程设定完毕，此时在其之上的有Channel又调用了IO阻塞操作，那么，这个Channel会被关闭，同时，该线程会立即受到一个`ClosedByInterruptException`异常，它的interrupt状态仍然保持不变。

​	Java传统IO是不支持中断的，所以如果代码在read/write等操作阻塞的话，是无法被中断的。这就无法和Thead的interrupt模型配合使用了。JavaNIO众多的升级点中就包含了IO操作对中断的支持。InterruptiableChannel表示支持中断的Channel。我们常用的FileChannel，SocketChannel，DatagramChannel都实现了这个接口。

 这个接口定义如下:

```java
public interface InterruptibleChannel
    extends Channel
{

    /**
     * Closes this channel.
     *
     * <p> Any thread currently blocked in an I/O operation upon this channel
     * will receive an {@link AsynchronousCloseException}.
     *
     * <p> This method otherwise behaves exactly as specified by the {@link
     * Channel#close Channel} interface.  </p>
     *
     * @throws  IOException  If an I/O error occurs
     */
    public void close() throws IOException;

}

```

其针对上面所提到逻辑的具体实现是在`java.nio.channels.spi.AbstractInterruptibleChannel`进行的，关于这个类的解析，我们来参考这篇文章[InterruptibleChannel 与可中断 IO](https://link.juejin.cn/?target=https%3A%2F%2Fgithub.com%2Fmuyinchen%2Fwoker%2Fblob%2Fmaster%2FNIO%2F%E8%A1%A5%E5%85%85%E6%BA%90%E7%A0%81%E8%A7%A3%E8%AF%BB%EF%BC%9AInterruptibleChannel%20%E4%B8%8E%E5%8F%AF%E4%B8%AD%E6%96%AD%20IO.md)

### 赋予Channel可被多路复用的能力（注册+取消注册+非阻塞）

​	我们在前面有说到，`Channel`可以被`Selector`进行使用，而`Selector`是根据`Channel`的状态来分配任务的，那么`Channel`应该提供一个注册到`Selector`上的方法，来和`Selector`进行绑定。也就是说`Channel`的实例要调用`register(Selector,int,Object)`。注意，因为`Selector`是要根据状态值进行管理的，所以此方法会返回一个`SelectionKey`对象来表示这个`channel`在`selector`上的状态。关于`SelectionKey`，它是包含很多东西的，这里暂不提。

```java
//java.nio.channels.spi.AbstractSelectableChannel#register
public final SelectionKey register(Selector sel, int ops, Object att)
        throws ClosedChannelException
    {
        if ((ops & ~validOps()) != 0)
            throw new IllegalArgumentException();
        if (!isOpen())
            throw new ClosedChannelException();
        synchronized (regLock) {
            if (isBlocking())
                throw new IllegalBlockingModeException();
            synchronized (keyLock) {
                // re-check if channel has been closed
                if (!isOpen())
                    throw new ClosedChannelException();
                SelectionKey k = findKey(sel);
                if (k != null) {
                    k.attach(att);
                    k.interestOps(ops);
                } else {
                    // New registration
                    k = ((AbstractSelector)sel).register(this, ops, att);
                    addKey(k);
                }
                return k;
            }
        }
    }
//java.nio.channels.spi.AbstractSelectableChannel#addKey
    private void addKey(SelectionKey k) {
        assert Thread.holdsLock(keyLock);
        int i = 0;
        if ((keys != null) && (keyCount < keys.length)) {
            // Find empty element of key array
            for (i = 0; i < keys.length; i++)
                if (keys[i] == null)
                    break;
        } else if (keys == null) {
            keys = new SelectionKey[2];
        } else {
            // Grow key array
            int n = keys.length * 2;
            SelectionKey[] ks =  new SelectionKey[n];
            for (i = 0; i < keys.length; i++)
                ks[i] = keys[i];
            keys = ks;
            i = keyCount;
        }
        keys[i] = k;
        keyCount++;
    }
```

一旦注册到`Selector`上，Channel将一直保持注册直到其被解除注册。在解除注册的时候会解除Selector分配给Channel的所有资源。 也就是Channel并没有直接提供解除注册的方法，那我们换一个思路，我们将Selector上代表其注册的Key取消不就可以了。这里可以通过调用`SelectionKey#cancel()`方法来显式的取消key。然后在`Selector`下一次选择操作期间进行对Channel的取消注册。

```java
//java.nio.channels.spi.AbstractSelectionKey#cancel
    /**
     * Cancels this key.
     *
     * <p> If this key has not yet been cancelled then it is added to its
     * selector's cancelled-key set while synchronized on that set.  </p>
     */
    public final void cancel() {
        // Synchronizing "this" to prevent this key from getting canceled
        // multiple times by different threads, which might cause race
        // condition between selector's select() and channel's close().
        synchronized (this) {
            if (valid) {
                valid = false;
                //还是调用Selector的cancel方法
                ((AbstractSelector)selector()).cancel(this);
            }
        }
    }


//java.nio.channels.spi.AbstractSelector#cancel
    void cancel(SelectionKey k) {                       
        synchronized (cancelledKeys) {
            cancelledKeys.add(k);
        }
    }


//在下一次select操作的时候来解除那些要求cancel的key，即解除Channel注册
//sun.nio.ch.SelectorImpl#select(long)
    @Override
    public final int select(long timeout) throws IOException {
        if (timeout < 0)
            throw new IllegalArgumentException("Negative timeout");
            //重点关注此方法
        return lockAndDoSelect(null, (timeout == 0) ? -1 : timeout);
    }
//sun.nio.ch.SelectorImpl#lockAndDoSelect
    private int lockAndDoSelect(Consumer<SelectionKey> action, long timeout)
        throws IOException
    {
        synchronized (this) {
            ensureOpen();
            if (inSelect)
                throw new IllegalStateException("select in progress");
            inSelect = true;
            try {
                synchronized (publicSelectedKeys) {
                    //重点关注此方法
                    return doSelect(action, timeout);
                }
            } finally {
                inSelect = false;
            }
        }
    }
//sun.nio.ch.WindowsSelectorImpl#doSelect
    protected int doSelect(Consumer<SelectionKey> action, long timeout)
        throws IOException
    {
        assert Thread.holdsLock(this);
        this.timeout = timeout; // set selector timeout
        processUpdateQueue();
        //重点关注此方法
        processDeregisterQueue();
        if (interruptTriggered) {
            resetWakeupSocket();
            return 0;
        }
        ...
    }

     /**
     * sun.nio.ch.SelectorImpl#processDeregisterQueue
     * Invoked by selection operations to process the cancelled-key set
     */
    protected final void processDeregisterQueue() throws IOException {
        assert Thread.holdsLock(this);
        assert Thread.holdsLock(publicSelectedKeys);

        Set<SelectionKey> cks = cancelledKeys();
        synchronized (cks) {
            if (!cks.isEmpty()) {
                Iterator<SelectionKey> i = cks.iterator();
                while (i.hasNext()) {
                    SelectionKeyImpl ski = (SelectionKeyImpl)i.next();
                    i.remove();

                    // remove the key from the selector
                    implDereg(ski);

                    selectedKeys.remove(ski);
                    keys.remove(ski);

                    // remove from channel's key set
                    deregister(ski);

                    SelectableChannel ch = ski.channel();
                    if (!ch.isOpen() && !ch.isRegistered())
                        ((SelChImpl)ch).kill();
                }
            }
        }
    }

```

这里，当Channel关闭时，无论是通过调用`Channel#close`还是通过打断线程的方式来对Channel进行关闭，其都会隐式的取消关于这个Channel的所有的keys，其内部也是调用了`k.cancel()`。

```java
//java.nio.channels.spi.AbstractInterruptibleChannel#close
    /**
     * Closes this channel.
     *
     * <p> If the channel has already been closed then this method returns
     * immediately.  Otherwise it marks the channel as closed and then invokes
     * the {@link #implCloseChannel implCloseChannel} method in order to
     * complete the close operation.  </p>
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public final void close() throws IOException {
        synchronized (closeLock) {
            if (closed)
                return;
            closed = true;
            implCloseChannel();
        }
    }
//java.nio.channels.spi.AbstractSelectableChannel#implCloseChannel
     protected final void implCloseChannel() throws IOException {
        implCloseSelectableChannel();

        // clone keys to avoid calling cancel when holding keyLock
        SelectionKey[] copyOfKeys = null;
        synchronized (keyLock) {
            if (keys != null) {
                copyOfKeys = keys.clone();
            }
        }

        if (copyOfKeys != null) {
            for (SelectionKey k : copyOfKeys) {
                if (k != null) {
                    k.cancel();   // invalidate and adds key to cancelledKey set
                }
            }
        }
    }

```

如果`Selector`自身关闭掉，那么Channel也会被解除注册，同时代表Channel注册的key也将变得无效：

```java
//java.nio.channels.spi.AbstractSelector#close
public final void close() throws IOException {
        boolean open = selectorOpen.getAndSet(false);
        if (!open)
            return;
        implCloseSelector();
    }
//sun.nio.ch.SelectorImpl#implCloseSelector
@Override
public final void implCloseSelector() throws IOException {
    wakeup();
    synchronized (this) {
        implClose();
        synchronized (publicSelectedKeys) {
            // Deregister channels
            Iterator<SelectionKey> i = keys.iterator();
            while (i.hasNext()) {
                SelectionKeyImpl ski = (SelectionKeyImpl)i.next();
                deregister(ski);
                SelectableChannel selch = ski.channel();
                if (!selch.isOpen() && !selch.isRegistered())
                    ((SelChImpl)selch).kill();
                selectedKeys.remove(ski);
                i.remove();
            }
            assert selectedKeys.isEmpty() && keys.isEmpty();
        }
    }
}

```

​	一个`channel`所支持的`Ops`（操作事件）中，假如支持多个`Ops`，在特定的`selector`注册一次之后便无法在该`selector`上重复注册，也就是在二次调用`java.nio.channels.spi.AbstractSelectableChannel#register`方法得到时候，只会进行Ops的改变，并不会重新注册，因为注册会产生一个全新的`SelectionKey`对象。我们可以通过调用`java.nio.channels.SelectableChannel#isRegistered`的方法来确定是否向一个或多个`Selector`注册了`channel`。

```java
//java.nio.channels.spi.AbstractSelectableChannel#isRegistered
 // -- Registration --

    public final boolean isRegistered() {
        synchronized (keyLock) {
            //我们在之前往Selector上注册的时候调用了addKey方法，即每次往//一个Selector注册一次，keyCount就要自增一次。
            return keyCount != 0;
        }
    }

```

​	至此，继承了SelectableChannel这个类之后，这个channel就可以安全的由多个并发线程来使用。 这里，要注意的是，继承了`AbstractSelectableChannel`这个类之后，新创建的channel始终处于阻塞模式。然而与`Selector`的多路复用有关的操作必须基于非阻塞模式，所以在注册到`Selector`之前，必须将`channel`置于非阻塞模式，并且在取消注册之前，`channel`可能不会返回到阻塞模式。 这里，我们涉及了Channel的阻塞模式与非阻塞模式。在阻塞模式下，在`Channel`上调用的每个I/O操作都将阻塞，直到完成为止。 在非阻塞模式下，I/O操作永远不会阻塞，并且可以传输比请求的字节更少的字节，或者根本不传输任何字节。 我们可以通过调用channel的isBlocking方法来确定其是否为阻塞模式。

```java
//java.nio.channels.spi.AbstractSelectableChannel#register
 public final SelectionKey register(Selector sel, int ops, Object att)
        throws ClosedChannelException
    {
        if ((ops & ~validOps()) != 0)
            throw new IllegalArgumentException();
        if (!isOpen())
            throw new ClosedChannelException();
        synchronized (regLock) {
     //此处会做判断，假如是阻塞模式，则会返回true，然后就会抛出异常
            if (isBlocking())
                throw new IllegalBlockingModeException();
            synchronized (keyLock) {
                // re-check if channel has been closed
                if (!isOpen())
                    throw new ClosedChannelException();
                SelectionKey k = findKey(sel);
                if (k != null) {
                    k.attach(att);
                    k.interestOps(ops);
                } else {
                    // New registration
                    k = ((AbstractSelector)sel).register(this, ops, att);
                    addKey(k);
                }
                return k;
            }
        }
    }

```

所以，我们在使用的时候可以基于以下的例子作为参考:

```java
public NIOServerSelectorThread(int port)
	{
		try {
			//打开ServerSocketChannel，用于监听客户端的连接，他是所有客户端连接的父管道
			serverSocketChannel = ServerSocketChannel.open();
			//将管道设置为非阻塞模式
			serverSocketChannel.configureBlocking(false);
			//利用ServerSocketChannel创建一个服务端Socket对象，即ServerSocket
			serverSocket = serverSocketChannel.socket();
			//为服务端Socket绑定监听端口
			serverSocket.bind(new InetSocketAddress(port));
			//创建多路复用器
			selector = Selector.open();
			//将ServerSocketChannel注册到Selector多路复用器上，并且监听ACCEPT事件
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			System.out.println("The server is start in port: "+port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

```

因时间关系，本篇暂时到这里，剩下的会在下一篇中进行讲解。