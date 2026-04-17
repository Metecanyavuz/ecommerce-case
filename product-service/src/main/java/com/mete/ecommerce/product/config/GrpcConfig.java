package com.mete.ecommerce.product.config;

import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

/**
 * gRPC Netty executor konfigürasyonu — benchmark adalet düzeltmesi.
 *
 * Problem: Tomcat (REST) varsayılan 200 thread kullanır.
 *          Netty (gRPC) varsayılan min(4, 2×CPU) thread kullanır.
 *          2 vCPU makinede gRPC 4 thread, REST 200 thread → S3 gibi yüksek
 *          eşzamanlılık senaryolarında gRPC thread açlığından zarar görür,
 *          protokol farkından değil.
 *
 * Çözüm: gRPC executor'ını Tomcat ile eşit thread pool boyutuna ayarla.
 *        GrpcProductService.listProducts() ve streamProducts() blokeli JPA
 *        çağrısı yaptığından her concurrent istek bir thread tutar — bu nedenle
 *        pool boyutu concurrent VU sayısıyla orantılı olmalıdır.
 *
 * Üretimde: Reactive gRPC (Project Reactor + grpc-kotlin/coroutines) ile
 *           event-loop modeline geçilirse bu konfigürasyona gerek kalmaz.
 */
@Configuration
public class GrpcConfig {

    private static final int THREAD_POOL_SIZE = 200;  // k6 S3 max VU = 200

    @Bean
    public GrpcServerConfigurer grpcServerConfigurer() {
        return serverBuilder -> {
            if (serverBuilder instanceof NettyServerBuilder nb) {
                nb.executor(Executors.newFixedThreadPool(THREAD_POOL_SIZE));
            }
        };
    }
}
