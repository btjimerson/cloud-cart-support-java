package dev.snbv2.cloudcart.catalog.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter that converts SSE-formatted MCP responses to pure JSON.
 * <p>
 * Spring AI's Streamable HTTP transport returns tools/list and other
 * responses as SSE events (id:, event:, data:) even for Streamable HTTP.
 * Some MCP clients (e.g., kagent) expect pure JSON responses.
 * This filter strips the SSE envelope and returns just the JSON data.
 */
@Configuration
public class McpJsonResponseFilter {

    private static final Pattern SSE_DATA_PATTERN = Pattern.compile("(?m)^data:(.+)$");

    @Bean
    public FilterRegistrationBean<Filter> mcpResponseFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {

                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;

                // Only intercept POST /mcp
                if (!"POST".equals(httpRequest.getMethod()) || !"/mcp".equals(httpRequest.getRequestURI())) {
                    chain.doFilter(request, response);
                    return;
                }

                // Wrap response to capture output
                StringWriter sw = new StringWriter();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                HttpServletResponseWrapper wrapper = new HttpServletResponseWrapper(httpResponse) {
                    private final PrintWriter writer = new PrintWriter(sw);

                    @Override
                    public PrintWriter getWriter() {
                        return writer;
                    }

                    @Override
                    public jakarta.servlet.ServletOutputStream getOutputStream() {
                        return new jakarta.servlet.ServletOutputStream() {
                            @Override
                            public void write(int b) {
                                baos.write(b);
                            }

                            @Override
                            public boolean isReady() {
                                return true;
                            }

                            @Override
                            public void setWriteListener(jakarta.servlet.WriteListener listener) {
                            }
                        };
                    }
                };

                chain.doFilter(request, wrapper);
                wrapper.getWriter().flush();

                String body = sw.toString();
                if (body.isEmpty()) {
                    body = baos.toString();
                }

                // If response contains SSE data: lines, extract the JSON
                if (body.contains("data:")) {
                    Matcher matcher = SSE_DATA_PATTERN.matcher(body);
                    if (matcher.find()) {
                        String jsonData = matcher.group(1).trim();
                        httpResponse.setContentType("application/json");
                        httpResponse.setContentLength(jsonData.length());
                        httpResponse.getWriter().write(jsonData);
                        httpResponse.getWriter().flush();
                        return;
                    }
                }

                // Pass through as-is
                httpResponse.getWriter().write(body);
                httpResponse.getWriter().flush();
            }
        });
        registration.addUrlPatterns("/mcp");
        registration.setOrder(1);
        return registration;
    }
}
