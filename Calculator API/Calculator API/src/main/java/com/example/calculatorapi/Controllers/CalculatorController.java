package com.example.calculatorapi.Controllers;

import com.example.calculatorapi.DTOs.WindowDTO;
import com.example.calculatorapi.Exceptions.TestServiceFailedException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;

@RestController
public class CalculatorController {

    private final int WINDOW_SIZE = 10;
    private final LinkedHashSet<Integer> numberWindow = new LinkedHashSet<>();
    private final RestTemplate restTemplate = new RestTemplate();

    private final String authUrl = "http://20.244.56.144/evaluation-service/auth";

    private final Map<String, String> urlMap = Map.of(
            "p", "http://20.244.56.144/evaluation-service/primes",
            "f", "http://20.244.56.144/evaluation-service/fibo",
            "e", "http://20.244.56.144/evaluation-service/even",
            "r", "http://20.244.56.144/evaluation-service/rand"
    );


    private String cachedToken = null;
    private long tokenExpiry = 0;

    @GetMapping("/numbers/{numberID}")
    public ResponseEntity<WindowDTO> getNumbers(@PathVariable String numberID) {
        if (!urlMap.containsKey(numberID)) {
            return ResponseEntity.badRequest().build();
        }

        String token = fetchAuthToken();
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Integer> prevWindowState = new ArrayList<>(numberWindow);
        List<Integer> newNumbers = fetchNumbersWithTimeout(urlMap.get(numberID), token, 500);

        newNumbers.removeIf(numberWindow::contains);

        for (Integer num : newNumbers) {
            if (numberWindow.size() >= WINDOW_SIZE) {
                Iterator<Integer> it = numberWindow.iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            numberWindow.add(num);
        }

        List<Integer> currWindowState = new ArrayList<>(numberWindow);
        double avg = numberWindow.stream().mapToDouble(i -> i).average().orElse(0.0);

        WindowDTO dto = new WindowDTO(prevWindowState, currWindowState, newNumbers, Math.round(avg * 100.0) / 100.0);
        return ResponseEntity.ok(dto);
    }

    private String fetchAuthToken() {
        // checking if valid still or not
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken;
        }

        //setting up credentials , in production move to environment
        Map<String, String> body = Map.of(
                "email", "shaunakbanerjee5@gmail.com",
                "name", "shaunak banerjee",
                "rollNo", "4ni22ci053",
                "accessCode", "DRYscE",
                "clientID", "0c6878f3-06b9-4a87-a6d6-87e52f7f3643",
                "clientSecret", "GpQxJesRsxVawSmq"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(authUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> bodyMap = response.getBody();
                cachedToken = (String) bodyMap.get("access_token");
                Integer expiresIn = (Integer) bodyMap.getOrDefault("expires_in", 300);
                tokenExpiry = System.currentTimeMillis() + expiresIn * 1000L;
                return cachedToken;
            }
        } catch (Exception e) {
            System.out.println("Token fetch failed: " + e.getMessage());
        }

        return null;
    }

    private List<Integer> fetchNumbersWithTimeout(String url, String token, int timeoutMillis) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<Integer>> future = executor.submit(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    Map<String, List<Integer>> responseBody = response.getBody();
                    return (List<Integer>) responseBody.get("numbers");
                }
            } catch (Exception e) {
                System.out.println("Fetch failed: " + e.getMessage());

            }
            return Collections.emptyList();
        });

        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            throw new TestServiceFailedException("took more than 500ms");
        } finally
{
        executor.shutdownNow();
    }
}
}
