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
 * アプリケーションのロギング設定をJavaコードで一元管理するクラス。
 * Spring Bootの起動時に、XMLファイルの代わりにこの設定が読み込まれます。
 * InitializingBeanを実装することで、SpringがこのBeanを初期化した後にafterPropertiesSet()メソッドが自動的に実行され、
 * ログ設定が適用される仕組みです。
 */
@Configuration // このクラスがSpringの設定クラスであることを示すアノテーション
public class LoggingConfiguration implements InitializingBean {

    /**
     * ルートロガーのログレベルをapplication.propertiesなどから取得します。
     * '${app.log.level}'というプロパティが存在しない場合は、デフォルト値として"INFO"が使用されます。
     * これにより、環境ごとにログレベルを柔軟に変更できます。（例：開発環境ではDEBUG, 本番環境ではINFO）
     */
    @Value("${app.log.level:INFO}")
    private String rootLevel;

    /**
     * このBeanのプロパティが全て設定された後にSpringによって呼び出されるメソッド。
     * ここでLogbackの全体設定をプログラムで構築します。
     */
    @Override
    public void afterPropertiesSet() {
        // 1. Logback設定の土台となるLoggerContextを取得します。
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // 2. Spring Bootが自動的に構成するデフォルトのログ設定を一度リセットします。
        //    これにより、意図しない設定が残るのを防ぎ、完全にこのクラスで制御できます。
        ctx.reset();

        /* ── I. エンコーダ（Encoder）の定義 ─────────────────────────── */
        // Encoderは、ログイベントをどのような書式（フォーマット）で出力するかを決定します。
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(ctx); // LoggerContextとの紐付け
        // ログの出力パターンを設定します。%d:日付, %-5level:ログレベル, %thread:スレッド名, %logger:ロガー名, %msg:メッセージ, %n:改行
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] [%logger] %msg%n");
        encoder.start(); // 設定を有効化

        /* ── II. アペンダ（Appender）の定義 ─────────────────────────── */
        // Appenderは、フォーマットされたログをどこに出力するか（出力先）を決定します。
        // ここでは、コンテナ環境の標準である標準出力（stdout）に出力するConsoleAppenderを使用します。
        ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
        console.setContext(ctx); // LoggerContextとの紐付け
        console.setEncoder(encoder); // 上で定義したエンコーダを設定

        // A) アペンダにフィルタを追加して、不要なログをここで弾きます。
        console.addFilter(healthCheckFilter());
        
        console.start(); // 設定を有効化

        /* ── III. ロガー（Logger）の設定 ─────────────────────────── */
        // ルートロガー（全てのロガーの最上位）を取得し、設定を適用します。
        ch.qos.logback.classic.Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        // application.properties等から取得したレベルをルートロガーに設定
        root.setLevel(Level.toLevel(rootLevel));
        // このロガーの出力先として、上で定義したConsoleAppenderを追加
        root.addAppender(console);

        /* ── IV. 特定パッケージのログレベルを個別に設定 ─────────────── */
        // アプリケーション全体（ルート）はINFOレベルでも、特定のライブラリが出力する冗長なログは
        // レベルを引き上げて（例：WARN）、本番環境でのノイズやコストを削減します。
        setPackageLevel(ctx, "com.zaxxer.hikari", Level.WARN); // HikariCP（DB接続プール）のINFOログ等を抑制
        setPackageLevel(ctx, "org.springframework.jdbc", Level.WARN); // SpringのJDBC関連ログを抑制
        setPackageLevel(ctx, "org.springframework.transaction", Level.WARN); // トランザクション関連ログを抑制
        setPackageLevel(ctx, "org.springframework.web", Level.INFO); // Web層はINFOレベルを維持（アクセスログなどを出すため）

        // 設定が完了したことを示すログを、設定後のロガーで出力します。
        log.info("Custom Logback configuration (EKS / ContainerInsights) initialized.");
    }

    /* ===== フィルタ定義 =============================== */

    /**
     * ヘルスチェックに関連するログメッセージを検知し、破棄（DENY）するためのフィルタを生成します。
     * @return Filter<ILoggingEvent> 設定済みのフィルタオブジェクト
     */
    private Filter<ILoggingEvent> healthCheckFilter() {
        return new Filter<>() { // 無名クラスでFilterインターフェースを実装
            @Override
            public FilterReply decide(ILoggingEvent ev) {
                // ログイベントからフォーマット済みのメッセージを取得
                String msg = ev.getFormattedMessage();
                
                // メッセージがnullでなく、かつ指定したいずれかの文字列を含んでいれば...
                if (msg != null && (
                        msg.contains("HEALTH_CHECK") ||          // 自前のヘルスチェック用キーワード
                        msg.contains("GET /actuator/health") ||  // Spring Actuatorのヘルスチェックエンドポイント
                        msg.contains("SELECT 1")                 // DB接続確認でよく使われるクエリ
                )) {
                    // このログイベントは破棄する（出力しない）
                    return FilterReply.DENY;
                }
                // 条件に一致しない場合は、特に何もしない（次のフィルタやレベル判定に任せる）
                return FilterReply.NEUTRAL;
            }
        };
    }

    /* ===== ヘルパーメソッド定義 =============================== */
    
    /**
     * 指定されたパッケージ（ロガー名）のログレベルを、指定されたレベルに変更するヘルパーメソッドです。
     * @param ctx LoggerContext
     * @param pkg パッケージ名 (例: "com.zaxxer.hikari")
     * @param level 設定したいログレベル (例: Level.WARN)
     */
    private void setPackageLevel(LoggerContext ctx, String pkg, Level level) {
        ch.qos.logback.classic.Logger l = ctx.getLogger(pkg);
        l.setLevel(level);
    }

    /**
     * このLoggingConfigurationクラス自身がログを出力するために使用するロガー。
     * static finalで定義するのが一般的です。
     */
    private static final Logger log = LoggerFactory.getLogger(LoggingConfiguration.class);
}