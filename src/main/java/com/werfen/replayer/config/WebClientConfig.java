package com.werfen.replayer.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(ReplayerProperties props) throws SSLException {
        SslContext sslContext = SslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build();

        HttpClient httpClient = HttpClient.create()
            .compress(true)
            .secure(spec -> spec.sslContext(sslContext))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                props.requestTimeoutSeconds() * 1000)
            .doOnConnected(conn -> conn.addHandlerLast(
                new ReadTimeoutHandler(props.requestTimeoutSeconds(), TimeUnit.SECONDS)));

        return WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
