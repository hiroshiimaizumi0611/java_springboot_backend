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

/**
 * Logback のプログラム構成を初期化する Spring 設定クラス。
 *
 * <p>主な内容:
 * <ul>
 *   <li>コンソール出力用アペンダと出力パターンの登録</li>
 *   <li>ルートロガーおよび一部パッケージのログレベル設定</li>
 *   <li>ヘルスチェック/接続確認（"HEALTH_CHECK", "GET /actuator/health", "SELECT 1"）メッセージの除外フィルタ</li>
 * </ul>
 *
 * <p>ログレベルはプロパティ {@code app.log.level}（未指定時は {@code DEBUG}）で制御します。
 * このクラスは Logback のコンテキストを {@link ch.qos.logback.classic.LoggerContext#reset() reset()} するため、
 * 外部の logback.xml や Spring Boot の標準 {@code logging.*} 設定は無効化されます。
 *
 * <p>コンテナ/クラウド環境（EKS/Container Insights 等）での標準出力収集を想定しています。
 *
 * @since 1.0
 */

@Configuration
public class LoggingConfiguration implements InitializingBean {

/**
 * ルートロガーのログレベルを外部プロパティ {@code app.log.level}
 * （未指定時は {@code DEBUG}）から読み込む。
 */
  @Value("${app.log.level:DEBUG}")
  private String rootLevel;

/**
 * アプリ起動時に Logback をプログラム的に初期化する。
 *
 * <p>以下を実施する:
 * <ul>
 *   <li>既存設定のリセット（{@link ch.qos.logback.classic.LoggerContext#reset()})</li>
 *   <li>コンソールアペンダと出力パターンの登録</li>
 *   <li>ヘルスチェック由来ログを除外するフィルタの付与</li>
 *   <li>ルート/特定パッケージのログレベル設定</li>
 * </ul>
 * プロパティ {@code app.log.level} をルートロガーに適用する。
 */
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

/**
 * ヘルスチェック/接続確認に起因するログを抑制するフィルタを生成する。
 *
 * <p>メッセージに {@code "HEALTH_CHECK"} または {@code "GET /actuator/health"} を含むイベントを
 * {@link ch.qos.logback.core.spi.FilterReply#DENY DENY} とし、
 * それ以外は {@link ch.qos.logback.core.spi.FilterReply#NEUTRAL NEUTRAL} を返す。
 *
 * @return コンソールアペンダに装着するログフィルタ
 */

private Filter<ILoggingEvent> healthCheckFilter() {
    return new Filter<>() {
      @Override
      public FilterReply decide(ILoggingEvent ev) {
        String msg = ev.getFormattedMessage();

        if (msg != null
            && (msg.contains("HEALTH_CHECK")
                || msg.contains("GET /actuator/health"))) {
          return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
      }
    };
  }

/**
 * 指定パッケージのロガーにログレベルを設定するヘルパー。
 *
 * @param ctx   ログバックのコンテキスト
 * @param pkg   対象パッケージ名（ロガー名）
 * @param level 設定するログレベル
 */
  private void setPackageLevel(LoggerContext ctx, String pkg, Level level) {
    ch.qos.logback.classic.Logger l = ctx.getLogger(pkg);
    l.setLevel(level);
  }

  private static final Logger log = LoggerFactory.getLogger(LoggingConfiguration.class);
}
