package sunghs.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.AbstractPagingItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.CollectionUtils;
import sunghs.model.ExampleEntity;
import sunghs.repository.DataRepository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ExampleJobConfiguration {

    private static final int CHUNK_SIZE = 100;

    private static final int PAGE_SIZE = 100;

    private static final int WORKER_SIZE = 10;

    private final JobBuilderFactory jobBuilderFactory;

    private final StepBuilderFactory stepBuilderFactory;

    private final DataRepository dataRepository;

    @Bean
    public Job exampleJob() {
        return jobBuilderFactory.get("exampleJob")
                .incrementer(new RunIdIncrementer())
                .start(exampleStep())
                .on("*")
                .end()
                .end()
                .preventRestart()
                .build();
    }

    @Bean
    @JobScope
    public Step exampleStep() {
        return stepBuilderFactory.get("exampleJob.exampleStep")
                .<ExampleEntity, ExampleEntity>chunk(CHUNK_SIZE)
                .reader(itemReader())
                .writer(itemWriter())
                .listener(jobExecutionListener(taskExecutor()))
                .taskExecutor(taskExecutor())
                .throttleLimit(WORKER_SIZE)
                .build();
    }

    @Bean
    @StepScope
    public AbstractPagingItemReader<ExampleEntity> itemReader() {
        return new AbstractPagingItemReader<>() {
            @Override
            protected void doReadPage() {
                if (CollectionUtils.isEmpty(results)) {
                    results = new ArrayList<>();
                } else {
                    results.clear();
                }

                setPageSize(PAGE_SIZE);

                PageRequest page = PageRequest.of(getPage(), getPageSize(), Sort.by("id").ascending());
                List<ExampleEntity> list = dataRepository.findAll(page).getContent();
                log.info("worker - {}, itemReader getListSize : {}", Thread.currentThread().getName(), list.size());
                results.addAll(list);
            }

            @Override
            protected void doJumpToPage(int itemIndex) {
            }
        };
    }

    @Bean
    @StepScope
    public ItemWriter<ExampleEntity> itemWriter() {
        return items -> {
            String itemList = items.stream()
                .map(exampleEntity -> String.valueOf(exampleEntity.getId()))
                .reduce((s, s2) -> s + "," + s2)
                .toString();
            log.info("worker - {}, items : {}", Thread.currentThread().getName(), itemList);
        };
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(WORKER_SIZE);
        threadPoolTaskExecutor.setMaxPoolSize(WORKER_SIZE);
        threadPoolTaskExecutor.setThreadNamePrefix("executor-");
        threadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        threadPoolTaskExecutor.setAllowCoreThreadTimeOut(true);
        threadPoolTaskExecutor.setKeepAliveSeconds(1);
        threadPoolTaskExecutor.initialize();
        return threadPoolTaskExecutor;
    }

    public JobExecutionListener jobExecutionListener(TaskExecutor taskExecutor) {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                log.info("exampleJob start");
            }

            @Override
            public void afterJob(JobExecution jobExecution) {
                ((ThreadPoolTaskExecutor) taskExecutor).shutdown();
            }
        };
    }
}
