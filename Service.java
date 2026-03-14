package java_weather_currency_dashboard;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class Service {

    private String country;
    private String currencyCode;

    // api.openweathermap.org
    private static final String OPENWEATHER_API_KEY = "YOUR_API_KEY";
    // api.fixer.io
    private static final String FIXER_API_KEY = "YOUR_API_KEY";

    private static final Map<String, String> COUNTRY_TO_CURRENCY = new HashMap<>();
    static {
        COUNTRY_TO_CURRENCY.put("Poland", "PLN");
        COUNTRY_TO_CURRENCY.put("Italy", "EUR");
        COUNTRY_TO_CURRENCY.put("Germany", "EUR");
        COUNTRY_TO_CURRENCY.put("France", "EUR");
        COUNTRY_TO_CURRENCY.put("Spain", "EUR");
        COUNTRY_TO_CURRENCY.put("USA", "USD");
        COUNTRY_TO_CURRENCY.put("United States", "USD");
        COUNTRY_TO_CURRENCY.put("Japan", "JPY");
        COUNTRY_TO_CURRENCY.put("UK", "GBP");
        COUNTRY_TO_CURRENCY.put("United Kingdom", "GBP");
        COUNTRY_TO_CURRENCY.put("Thailand", "THB");
        COUNTRY_TO_CURRENCY.put("Switzerland", "CHF");
        COUNTRY_TO_CURRENCY.put("Australia", "AUD");
        COUNTRY_TO_CURRENCY.put("Canada", "CAD");
        COUNTRY_TO_CURRENCY.put("China", "CNY");
    }

    public Service(String country) {
        this.country = country;
        this.currencyCode = COUNTRY_TO_CURRENCY.getOrDefault(country, "USD");
    }

    public String getCurrencyCode() {
        return currencyCode;
    }
    private URI buildUri(String base, Map<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder(base).append("?");
        for (var e : params.entrySet()) {
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append("=").append(URLEncoder.encode(e.getValue(), "UTF-8")).append("&");
        }
        sb.setLength(sb.length() - 1);
        return new URI(sb.toString());
    }

    private String getResponse(URI uri) throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new java.security.SecureRandom());

        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();

        if (conn instanceof HttpsURLConnection https) {
            https.setSSLSocketFactory(sc.getSocketFactory());
            https.setHostnameVerifier((hostname, session) -> true);
        }

        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
    private String extractJson(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;

        char first = json.charAt(start);
        if (first == '"') {
            int end = json.indexOf('"', start + 1);
            return end > start ? json.substring(start + 1, end) : null;
        } else {
            int end = start;
            while (end < json.length() && ",}]\n\r".indexOf(json.charAt(end)) < 0) end++;
            return json.substring(start, end).trim();
        }
    }
    public String getWeather(String city) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("q", city + "," + country);
            params.put("appid", OPENWEATHER_API_KEY);
            params.put("units", "metric");
            params.put("lang", "en");

            URI uri = buildUri("https://api.openweathermap.org/data/2.5/weather", params);
            return getResponse(uri);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
    public String getWeatherSummary(String city) {
        String json = getWeather(city);
        if (json.contains("\"error\"")) return "Could not fetch weather data.\n" + json;

        String desc = extractJson(json, "description");
        String temp = extractJson(json, "temp");
        String feelsLike = extractJson(json, "feels_like");
        String humidity = extractJson(json, "humidity");
        String windSpeed = extractJson(json, "speed");
        String cityName = extractJson(json, "name");

        return String.format(
                "City       : %s%n" +
                        "Conditions : %s%n" +
                        "Temperature: %s °C (feels like %s °C)%n" +
                        "Humidity   : %s%%%n" +
                        "Wind speed : %s m/s",
                cityName != null ? cityName : city,
                desc      != null ? desc      : "N/A",
                temp      != null ? temp      : "N/A",
                feelsLike != null ? feelsLike : "N/A",
                humidity  != null ? humidity  : "N/A",
                windSpeed != null ? windSpeed : "N/A"
        );
    }
    public Double getRateFor(String targetCurrency) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("access_key", FIXER_API_KEY);
            params.put("symbols", currencyCode + "," + targetCurrency);

            URI uri = buildUri("https://data.fixer.io/api/latest", params);
            String json = getResponse(uri);
            if (json.contains("\"success\":false")) {
                System.err.println("Fixer error: " + json);
                return null;
            }

            int ratesStart = json.indexOf("\"rates\":{");
            int ratesEnd   = json.indexOf("}", ratesStart);
            if (ratesStart < 0 || ratesEnd < 0) return null;

            String block = json.substring(ratesStart + 9, ratesEnd + 1);
            String[] entries = block.replace("{", "").replace("}", "").split(",");

            Double baseRate   = null;
            Double targetRate = null;

            for (String entry : entries) {
                String[] pair = entry.replace("\"", "").split(":");
                if (pair.length < 2) continue;
                String code  = pair[0].trim();
                double value = Double.parseDouble(pair[1].trim());
                if (code.equals(currencyCode))   baseRate   = value;
                if (code.equals(targetCurrency)) targetRate = value;
            }

            if (baseRate == null || baseRate == 0 || targetRate == null) return null;
            if (currencyCode.equals("EUR")) baseRate = 1.0;
            return targetRate / baseRate;

        } catch (Exception e) {
            System.err.println("getRateFor error: " + e.getMessage());
            return null;
        }
    }
    public Double getNBPRate() {
        if (currencyCode.equals("PLN")) return null;

        for (String table : new String[]{"A", "B", "C"}) {
            try {
                URI uri = new URI("https://api.nbp.pl/api/exchangerates/rates/" + table + "/" + currencyCode + "/?format=json");
                String json = getResponse(uri);
                int midIdx = json.indexOf("\"mid\":");
                if (midIdx < 0) continue;

                int start = midIdx + 6;
                while (start < json.length() && json.charAt(start) == ' ') start++;
                int end = start;
                while (end < json.length() && ",}]\n\r".indexOf(json.charAt(end)) < 0) end++;

                String midStr = json.substring(start, end).trim();
                return Double.parseDouble(midStr);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public String getWikiUrl(String city) {
        try {
            return "https://en.wikipedia.org/wiki/" +
                    URLEncoder.encode(city.replace(" ", "_"), "UTF-8");
        } catch (Exception e) {
            return "https://en.wikipedia.org/wiki/" + city.replace(" ", "_");
        }
    }

    public String getHTML() {
        return "<html><body><h1>Weather and currency service</h1></body></html>";
    }
}