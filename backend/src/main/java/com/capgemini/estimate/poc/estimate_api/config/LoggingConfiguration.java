package com.capgemini.estimate.poc.estimate_api.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingConfiguration implements InitializingBean {

    @Value("${app.log.level:INFO}")
    private String rootLevel;

    @Override
    public void afterPropertiesSet() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        ctx.reset();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(ctx);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] [%logger] %msg%n");
        encoder.start();

        ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
        console.setContext(ctx);
        console.setEncoder(encoder);

        console.addFilter(healthCheckFilter());
        
        console.start();

        ch.qos.logback.classic.Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);

        root.setLevel(Level.toLevel(rootLevel));
        root.addAppender(console);

        setPackageLevel(ctx, "com.zaxxer.hikari", Level.WARN);
        setPackageLevel(ctx, "org.springframework.jdbc", Level.WARN);
        setPackageLevel(ctx, "org.springframework.transaction", Level.WARN);
        setPackageLevel(ctx, "org.springframework.web", Level.INFO);

        log.info("Custom Logback configuration (EKS / ContainerInsights) initialized.");
    }

    private Filter<ILoggingEvent> healthCheckFilter() {
        return new Filter<>() {
            @Override
            public FilterReply decide(ILoggingEvent ev) {
                String msg = ev.getFormattedMessage();
                
                if (msg != null && (
                        msg.contains("HEALTH_CHECK") ||
                        msg.contains("GET /actuator/health") ||
                        msg.contains("SELECT 1")
                )) {
                    return FilterReply.DENY;
                }
                return FilterReply.NEUTRAL;
            }
        };
    }

    private void setPackageLevel(LoggerContext ctx, String pkg, Level level) {
        ch.qos.logback.classic.Logger l = ctx.getLogger(pkg);
        l.setLevel(level);
    }

    private static final Logger log = LoggerFactory.getLogger(LoggingConfiguration.class);
}