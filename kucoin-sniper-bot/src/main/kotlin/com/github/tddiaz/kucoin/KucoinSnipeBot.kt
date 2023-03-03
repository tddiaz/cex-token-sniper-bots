package com.github.tddiaz.kucoin

import com.kucoin.sdk.KucoinClientBuilder
import com.kucoin.sdk.exception.KucoinApiException
import com.kucoin.sdk.model.enums.ApiKeyVersionEnum
import com.kucoin.sdk.rest.request.OrderCreateApiRequest
import com.kucoin.sdk.rest.response.OrderCreateResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.concurrent.*

@SpringBootApplication
@EnableConfigurationProperties
class KucoinSnipeBot

fun main(args: Array<String>) {
    runApplication<KucoinSnipeBot>(*args)
}

@ConfigurationProperties(prefix = "kucoin.api")
@Component
class KucoinProps {
    lateinit var key: String
    lateinit var secret: String
    lateinit var passphrase: String
    lateinit var tradePair: String
}

@Component
class BotStarter(
    private val snipeBot: SnipeBot
) : ApplicationListener<ContextRefreshedEvent> {

    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        snipeBot.execute()
    }
}

@Component
class SnipeBot(
    private val kucoinProps: KucoinProps
) : InitializingBean {

    companion object {
        private val logger = LoggerFactory.getLogger(SnipeBot::class.java)
    }

    private lateinit var executorService: ExecutorService

    fun execute() {
        val kucoinApi = KucoinClientBuilder()
            .withApiKey(kucoinProps.key, kucoinProps.secret, kucoinProps.passphrase)
            .withApiKeyVersion(ApiKeyVersionEnum.V2.version)
            .buildRestClient()

        val futureOrderResponse = ArrayList<Future<OrderCreateResponse>>(1000)

        while (futureOrderResponse.size != 1000) {
            // create market order
            val orderCreateApiRequest = OrderCreateApiRequest.builder()
                .clientOid(System.currentTimeMillis().toString())
                .symbol(kucoinProps.tradePair)
                .side("buy")
                .tradeType("TRADE")
                .type("market")
                .funds(BigDecimal.valueOf(600L))
                .build()

            Thread.sleep(100) // adjust depending on api rate limit

            try {
                val future: Future<OrderCreateResponse> = executorService.submit(
                    Callable {
                        kucoinApi.orderAPI().createOrder(orderCreateApiRequest)
                    }
                )

                futureOrderResponse.add(future)

            } catch (e: Exception) {
                logger.error("${e.message}")
            }
        }

        futureOrderResponse.forEach {
            try {
                val response = it.get()
                logger.info("{}", response)
            } catch (e: ExecutionException) {
                if (e.cause is  KucoinApiException) {
                    val apiError = e.cause as KucoinApiException
                    logger.error("{} - {}", apiError.code, apiError.message)
                }
            }
        }
    }

    override fun afterPropertiesSet() {
        this.executorService = Executors.newVirtualThreadPerTaskExecutor()
    }
}


