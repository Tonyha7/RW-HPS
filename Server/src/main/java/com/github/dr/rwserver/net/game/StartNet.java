package com.github.dr.rwserver.net.game;

import com.github.dr.rwserver.data.global.Data;
import com.github.dr.rwserver.data.global.NetStaticData;
import com.github.dr.rwserver.net.core.AbstractNetConnect;
import com.github.dr.rwserver.net.udp.ReliableServerSocket;
import com.github.dr.rwserver.net.web.realization.HttpServer;
import com.github.dr.rwserver.net.web.realization.constant.HttpsSetting;
import com.github.dr.rwserver.struct.OrderedMap;
import com.github.dr.rwserver.struct.Seq;
import com.github.dr.rwserver.util.IsUtil;
import com.github.dr.rwserver.util.log.Log;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Dr
 * 遵守NetGameServer服务对外最少开放接口,尽量内部整合
 */
public class StartNet {
    private final Seq<Channel> connectChannel = new Seq<>(4);
    private ServerSocket serverSocket = null;
    private StartGameNetUdp startGameNetUdp = null;
    private final StartGameNetTcp starta = new StartGameNetTcp(this);
    protected final OrderedMap<String, AbstractNetConnect> OVER_MAP = new OrderedMap<>(16);

    public void openPort(int port) {
        /* boss用来接收进来的连接 */
        EpollEventLoopGroup bossGroup = new EpollEventLoopGroup(4);
        EpollEventLoopGroup workerGroup = new EpollEventLoopGroup();
        try {
            ServerBootstrap serverBootstrapTcp = new ServerBootstrap();
            serverBootstrapTcp.group(bossGroup, workerGroup)
                    .channel(EpollServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(port))
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    //.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childHandler(starta);

            ChannelFuture channelFutureTcp = serverBootstrapTcp.bind(port).sync();
            Channel start = channelFutureTcp.channel();

            connectChannel.add(start);

            Data.config.setObject("runPid",Data.core.getPid());
            Data.config.save();
            Log.clog(Data.localeUtil.getinput("server.start.openPort"));
            if (Data.game.webApi) {
                startWebApi();
            }
            start.closeFuture().sync();
        } catch (InterruptedException e) {
            Log.error("[TCP Start Error]", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public void openPort() {
        EpollEventLoopGroup bossGroup = new EpollEventLoopGroup(4);
        EpollEventLoopGroup workerGroup = new EpollEventLoopGroup();
        try {
            ServerBootstrap serverBootstrapTcp = new ServerBootstrap();
            serverBootstrapTcp.group(bossGroup, workerGroup)
                    .channel(EpollServerSocketChannel.class)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childHandler(new StartGameNetTcp(this));
            ChannelFuture nn = serverBootstrapTcp.bind(5200);
            for (int i=5201;i<5501;i++) {
                serverBootstrapTcp.bind(i);
            }
            Channel start = nn.channel();
            connectChannel.add(start);
            Log.clog(Data.localeUtil.getinput("server.start.openPort"));
            start.closeFuture().sync();
        } catch (InterruptedException e) {
            Log.error("[TCP Start Error]",e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public void startUdp(int port) {
        startGameNetUdp = new StartGameNetUdp(this, NetStaticData.protocolData.abstractNetConnect,NetStaticData.protocolData.typeConnect);
        try (ReliableServerSocket serverSocket = new ReliableServerSocket(port)) {
            this.serverSocket = serverSocket;
            do {
                final Socket socket = serverSocket.accept();
                startGameNetUdp.run((ReliableServerSocket.ReliableClientSocket) socket);
            } while (true);
        } catch (Exception ignored) {
            Log.error("UDP Start Error",ignored);
        }
    }

    public void startWebApi() {
        HttpServer httpServer = new HttpServer();
        HttpsSetting.sslEnabled = Data.game.webApiSsl;
        HttpsSetting.keystorePath = Data.game.webApiSslKetPath;
        HttpsSetting.certificatePassword = Data.game.webApiSslPasswd;
        HttpsSetting.keystorePassword =  Data.game.webApiSslPasswd;
        httpServer.start(Data.game.webApiPort, "com.github.dr.rwserver.net.web.api", 1024,
                null, null);
    }

    public void stop() {
        connectChannel.each(ChannelOutboundInvoker::close);
    }

    public void clear(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        AbstractNetConnect con = OVER_MAP.get(channel.id().asLongText());
        if (con != null) {
            try {
                con.disconnect();
                ctx.close();
            } catch (Exception e) {
                Log.info(e);
            }
        }
        OVER_MAP.remove(channel.id().asLongText());
    }

    public void updateNet() {
        if (IsUtil.notIsBlank(starta)) {
            starta.updateNet();
        }
        if (IsUtil.notIsBlank(serverSocket)) {
            startGameNetUdp.update();
        }
    }
}
