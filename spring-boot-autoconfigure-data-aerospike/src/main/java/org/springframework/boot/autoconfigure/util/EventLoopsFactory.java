package org.springframework.boot.autoconfigure.util;

import com.aerospike.client.async.EventLoopType;
import com.aerospike.client.async.EventLoops;
import com.aerospike.client.async.EventPolicy;
import com.aerospike.client.async.NioEventLoops;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.aerospike.AerospikeProperties;
import org.springframework.util.StringUtils;

/**
 * Factory for creating EventLoops for reactive Aerospike clients.
 * This class is isolated to avoid loading Netty classes when not needed.
 * Should only be used in reactive modules where Netty is a required dependency.
 *
 * @author Anastasiia Smirnova
 */
@Slf4j
public class EventLoopsFactory {

    /**
     * Creates and configures EventLoops for reactive operations.
     *
     * @param eventLoopsProperties the configuration properties for EventLoops
     * @return configured EventLoops instance
     */
    public static EventLoops createEventLoops(AerospikeProperties.EventLoopsProperties eventLoopsProperties) {
        EventPolicy eventPolicy = buildEventPolicy(eventLoopsProperties);
        String type = eventLoopsProperties.eventLoopsType;

        if (!StringUtils.hasText(type)) {
            throw new UnsupportedOperationException("Expecting 'nio' or 'netty' as eventLoopsType");
        }

        return switch (type.toLowerCase()) {
            case "netty" -> NettyEventLoopsHolder.createNettyEventLoops(eventPolicy, eventLoopsProperties);
            case "nio" -> createNioEventLoops(eventPolicy, eventLoopsProperties);
            default -> throw new UnsupportedOperationException(
                    "Expecting 'nio' or 'netty' as eventLoopsType, got '%s' instead".formatted(type)
            );
        };
    }

    private static EventPolicy buildEventPolicy(AerospikeProperties.EventLoopsProperties eventLoopsProperties) {
        EventPolicy eventPolicy = new EventPolicy();

        if (eventLoopsProperties.maxCommandsInProcess > 0) {
            eventPolicy.maxCommandsInProcess = eventLoopsProperties.maxCommandsInProcess;
        }
        if (eventLoopsProperties.maxCommandsInQueue > 0) {
            eventPolicy.maxCommandsInQueue = eventLoopsProperties.maxCommandsInQueue;
        }
        if (eventLoopsProperties.queueInitialCapacity > 0) {
            eventPolicy.queueInitialCapacity = eventLoopsProperties.queueInitialCapacity;
        }
        if (eventLoopsProperties.minTimeout > 0) {
            eventPolicy.minTimeout = eventLoopsProperties.minTimeout;
        }
        if (eventLoopsProperties.ticksPerWheel > 0) {
            eventPolicy.ticksPerWheel = eventLoopsProperties.ticksPerWheel;
        }
        if (eventLoopsProperties.commandsPerEventLoop > 0) {
            eventPolicy.commandsPerEventLoop = eventLoopsProperties.commandsPerEventLoop;
        }

        return eventPolicy;
    }

    private static EventLoops createNioEventLoops(EventPolicy eventPolicy,
                                               AerospikeProperties.EventLoopsProperties eventLoopsProperties) {
        int threads = Math.max(eventLoopsProperties.getThreads(), 0);
        boolean useDaemonThreads = eventLoopsProperties.nioDaemonThreads;
        String poolName = eventLoopsProperties.nioPoolName;
        return new NioEventLoops(eventPolicy, threads, useDaemonThreads, poolName);
    }

    /**
     * Nested static class to isolate Netty dependencies.
     * Netty classes will only be loaded when this class is first accessed.
     */
    private static class NettyEventLoopsHolder {

        private record EventLoopSelection(EventLoopGroup group, EventLoopType type) {}

        static EventLoops createNettyEventLoops(EventPolicy eventPolicy,
                                                AerospikeProperties.EventLoopsProperties eventLoopsProperties) {
            EventLoopSelection selection = chooseLoopGroup(eventLoopsProperties);
            // Import moved here - only loaded when needed
            return new com.aerospike.client.async.NettyEventLoops(
                    eventPolicy,
                    selection.group(),
                    selection.type()
            );
        }

        private static EventLoopSelection chooseLoopGroup(
                AerospikeProperties.EventLoopsProperties eventLoopsProperties) {
            int threadsNumber = Math.max(eventLoopsProperties.getThreads(), 0);

            if (!StringUtils.hasText(eventLoopsProperties.groupType)) {
                log.info("Proceeding with standard EventLoops group type 'NioEventLoopGroup'");
                return new EventLoopSelection(
                        new NioEventLoopGroup(threadsNumber), EventLoopType.NETTY_NIO);
            }

            return switch (eventLoopsProperties.groupType.toLowerCase()) {
                case "epolleventloopgroup" -> createEpollEventLoopGroup(threadsNumber);
                case "kqueueeventloopgroup" -> createKQueueEventLoopGroup(threadsNumber);
                case "nioeventloopgroup" -> new EventLoopSelection(
                        new NioEventLoopGroup(threadsNumber), EventLoopType.NETTY_NIO);
                default -> {
                    log.warn("Unexpected EventLoops group type '{}', proceeding with 'NioEventLoopGroup' instead",
                            eventLoopsProperties.groupType);
                    yield new EventLoopSelection(
                            new NioEventLoopGroup(threadsNumber), EventLoopType.NETTY_NIO
                    );
                }
            };
        }

        private static EventLoopSelection createEpollEventLoopGroup(int threadsNumber) {
            if (io.netty.channel.epoll.Epoll.isAvailable()) {
                log.info("Using EpollEventLoopGroup for native Linux transport");
                return new EventLoopSelection(
                        new EpollEventLoopGroup(threadsNumber), EventLoopType.NETTY_EPOLL
                );
            } else {
                log.warn("EpollEventLoopGroup requested but Epoll is not available on this platform. " +
                                "Cause: {}. Falling back to NioEventLoopGroup.",
                        io.netty.channel.epoll.Epoll.unavailabilityCause() != null
                                ? io.netty.channel.epoll.Epoll.unavailabilityCause().getMessage()
                                : "unknown");
                return new EventLoopSelection(
                        new NioEventLoopGroup(threadsNumber), EventLoopType.NETTY_NIO
                );
            }
        }

        private static EventLoopSelection createKQueueEventLoopGroup(int threadsNumber) {
            if (io.netty.channel.kqueue.KQueue.isAvailable()) {
                log.info("Using KQueueEventLoopGroup for native macOS/BSD transport");
                return new EventLoopSelection(
                        new KQueueEventLoopGroup(threadsNumber), EventLoopType.NETTY_KQUEUE
                );
            } else {
                log.warn("KQueueEventLoopGroup requested but KQueue is not available on this platform. " +
                                "Cause: {}. Falling back to NioEventLoopGroup.",
                        io.netty.channel.kqueue.KQueue.unavailabilityCause() != null
                                ? io.netty.channel.kqueue.KQueue.unavailabilityCause().getMessage()
                                : "unknown");
                return new EventLoopSelection(
                        new NioEventLoopGroup(threadsNumber), EventLoopType.NETTY_NIO);
            }
        }
    }
}
