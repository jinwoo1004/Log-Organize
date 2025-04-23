package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;

@SpringBootApplication
public class LogorganizeApplication implements CommandLineRunner {
	

    private static final Logger logger = LoggerFactory.getLogger(LogorganizeApplication.class);

    @Value("${log.file.path}")
    private String logFilePath;

    @Value("${output.file.path}")
    private String outputFilePath;

    public static void main(String[] args) {
        SpringApplication.run(LogorganizeApplication.class, args);
    }

    @Override
    public void run(String... args) {
        analyzeLogs();
    }

    public void analyzeLogs() {
        // 데이터 구조 초기화
        Map<String, Integer> apiKeyCounts = new ConcurrentHashMap<>();
        Map<String, Integer> apiServiceCounts = new ConcurrentHashMap<>();
        Map<String, Integer> browserCounts = new ConcurrentHashMap<>();
        AtomicInteger totalRequests = new AtomicInteger(0);

        // 로그 파일 읽기
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(logFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                processLogLine(line, apiKeyCounts, apiServiceCounts, browserCounts);
                totalRequests.incrementAndGet();
            }

            // 결과 파일에 기록
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFilePath))) {
                // 최다 호출 APIKEY 및 호출 건수
                if (!apiKeyCounts.isEmpty()) {
                    Map.Entry<String, Integer> maxApiKeyEntry = Collections.max(apiKeyCounts.entrySet(), Map.Entry.comparingByValue());
                    String maxApiKey = maxApiKeyEntry.getKey();
                    int maxApiKeyCount = maxApiKeyEntry.getValue();
                    writer.println("1. 최다 호출 APIKEY: " + maxApiKey + " (호출 수: " + maxApiKeyCount + ")");
                } else {
                    writer.println("1. 최다 호출 APIKEY: 데이터 없음");
                }

                // 상위 3개의 API Service ID
                List<Map.Entry<String, Integer>> sortedApiServiceEntries = new ArrayList<>(apiServiceCounts.entrySet());
                sortedApiServiceEntries.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));
                writer.println("2. 상위 3개의 API Service ID 및 요청 수:");
                for (int i = 0; i < Math.min(3, sortedApiServiceEntries.size()); i++) {
                    Map.Entry<String, Integer> entry = sortedApiServiceEntries.get(i);
                    writer.println("   " + entry.getKey() + ": " + entry.getValue());
                }

                // 웹 브라우저별 사용 비율
                writer.println("3. 웹 브라우저별 사용 비율:");
                for (Map.Entry<String, Integer> entry : browserCounts.entrySet()) {
                    String browser = entry.getKey();
                    int count = entry.getValue();
                    double percentage = (count / (double) totalRequests.get()) * 100;
                    writer.printf("   %s: %.2f%%\n", browser, percentage);
                }

                // 작업 완료 로그 기록
                logger.info("로그 분석 작업이 완료되었습니다. 결과는 " + outputFilePath + "에 저장되었습니다.");

            } catch (IOException e) {
                logger.error("결과 파일을 작성하는 중 오류가 발생했습니다.", e);
            }

        } catch (IOException e) {
            logger.error("로그 파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }

    private void processLogLine(String line, Map<String, Integer> apiKeyCounts, Map<String, Integer> apiServiceCounts, Map<String, Integer> browserCounts) {
        // 로그 라인에서 정보 추출
        Pattern pattern = Pattern.compile("\\[(\\d{3})]\\[http://apis\\.daum\\.net/search/([\\w]+)\\?apikey=(\\w+)&q=[^]]+\\]\\[(IE|Firefox|Safari|Chrome|Opera)\\]\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\]");
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches()) {
            String statusCode = matcher.group(1);
            if ("200".equals(statusCode)) { // 상태 코드가 200인 경우에만 카운트
                String apiKey = matcher.group(3);
                String apiServiceId = matcher.group(2);
                String browser = matcher.group(4);

                // APIKEY 카운트
                apiKeyCounts.merge(apiKey, 1, Integer::sum);

                // API Service ID 카운트
                apiServiceCounts.merge(apiServiceId, 1, Integer::sum);

                // 웹 브라우저 카운트
                browserCounts.merge(browser, 1, Integer::sum);
            }
        } else {
            // 디버깅을 위한 로그 출력 (문제가 있을 경우)
        	logger.warn("[200] 코드가 아닌 경우 분석 제외 라인 => " + line);
        }
    }
}