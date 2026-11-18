package org.example.qispringboot.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.ingestion.enabled",havingValue="true")
public class IngestionQueue {
    public static final String QUEUE="mental.rag.ingestion";
    private static final Logger log=LoggerFactory.getLogger(IngestionQueue.class);
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final RagService rag;
    public IngestionQueue(JdbcTemplate jdbc,RabbitTemplate rabbit,RagService rag) {this.jdbc=jdbc;this.rabbit=rabbit;this.rag=rag;}
    @Bean Declarables ingestionTopology() {
        return new Declarables(new DirectExchange("mental.rag",true,false),
                new DirectExchange("mental.rag.dead",true,false),
                QueueBuilder.durable(QUEUE).deadLetterExchange("mental.rag.dead").deadLetterRoutingKey("failed").build(),
                new Queue(QUEUE+".dead",true),
                new Binding(QUEUE,Binding.DestinationType.QUEUE,"mental.rag","ingest",null),
                new Binding(QUEUE+".dead",Binding.DestinationType.QUEUE,"mental.rag.dead","failed",null));
    }
    @Scheduled(fixedDelayString="${app.ingestion.poll-ms:5000}")
    public void publishPending() {
        // 恢复发布过程中退出的任务。重复投递由消费者事务和文档状态去重。
        jdbc.update("UPDATE ingestion_task SET status='PENDING' WHERE status='SENDING' AND updated_at<?",LocalDateTime.now().minusMinutes(1));
        var ids=jdbc.queryForList("SELECT id FROM ingestion_task WHERE status='PENDING' ORDER BY created_at LIMIT 10",String.class);
        for(String id:ids) {
            if(jdbc.update("UPDATE ingestion_task SET status='SENDING',attempts=attempts+1,updated_at=? WHERE id=? AND status='PENDING'",LocalDateTime.now(),id)!=1) continue;
            try {
                var correlation=new CorrelationData(id);
                rabbit.convertAndSend("mental.rag","ingest",id,correlation);
                if(!correlation.getFuture().get(4,TimeUnit.SECONDS).isAck() || correlation.getReturned()!=null) throw new IllegalStateException("not confirmed");
                jdbc.update("UPDATE ingestion_task SET status='PUBLISHED',updated_at=? WHERE id=? AND status='SENDING'",LocalDateTime.now(),id);
            } catch(Exception error) {
                log.warn("RAG消息发布未确认，任务{}等待重试",id);
                Integer attempts=jdbc.queryForObject("SELECT attempts FROM ingestion_task WHERE id=?",Integer.class,id);
                if(attempts!=null && attempts>=3) rag.failed(id);
                else jdbc.update("UPDATE ingestion_task SET status='PENDING',updated_at=? WHERE id=? AND status='SENDING'",LocalDateTime.now(),id);
            }
        }
    }
    @RabbitListener(queues=QUEUE)
    public void consume(String taskId) {
        if(taskId==null || !taskId.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("invalid task id");
        try {rag.ingest(taskId);}
        catch(RuntimeException error) {rag.failed(taskId);throw error;}
    }
}
