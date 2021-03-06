package com.lmx.jredis.transport;

import com.lmx.jredis.core.BusHelper;
import com.lmx.jredis.core.RedisServer;
import com.lmx.jredis.core.SimpleRedisServer;
import com.lmx.jredis.core.datastruct.SimpleHash;
import com.lmx.jredis.core.datastruct.SimpleKV;
import com.lmx.jredis.core.datastruct.SimpleList;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import redis.RedisCommandDecoder;
import redis.RedisReplyEncoder;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
@Order(value = 1)
@Slf4j
public class NettyServer {

    @Value("${rpcServer.host:0.0.0.0}")
    String host;
    @Value("${rpcServer.ioThreadNum:1}")
    int ioThreadNum;
    @Value("${rpcServer.backlog:1024}")
    int backlog;
    @Value("${rpcServer.port:16990}")
    int port;
    EventLoopGroup bossGroup;
    EventLoopGroup workerGroup;
    @Autowired
    SimpleKV simpleKV;
    @Autowired
    BusHelper busHelper;
    @Autowired
    SimpleList sl;
    @Autowired
    SimpleHash sh;
    @Autowired
    NettyServerHandler nettyServerHandler;

    @PostConstruct
    public void start() throws InterruptedException {
        log.info("start jredis server...");
        bossGroup = new NioEventLoopGroup(ioThreadNum);
        workerGroup = new NioEventLoopGroup(ioThreadNum);
        final RedisServer redis = new SimpleRedisServer();
        nettyServerHandler.init(redis);
        redis.initStore(simpleKV, sl, sh, busHelper);
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100)
                .localAddress(port)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new RedisCommandDecoder());
                        p.addLast(new RedisReplyEncoder());
                        p.addLast(nettyServerHandler);
                    }
                });
        // Start the server.
        serverBootstrap.bind(host, port).sync();
        log.info("jredis server listening on port {}", port);
    }


    @PreDestroy
    public void stop() {
        log.info("destroy server resources");
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
