/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.elasticsearch;


import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.LineReader;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

public class CatMetricsClient {

    private static final Logger logger = Logger.getLogger(CatMetricsClient.class);
    public static final String METRIC_PATH_SEPARATOR = "|";

    public Map<String,String> extractMetrics(String payload,List<String> metricKeys,String metricPrefix){
        Map<String,String> metricsMap = Maps.newHashMap();
        try {
            LineReader reader = new LineReader(new StringReader(payload));
            List<String> headers = parseALine(reader.readLine());
            List<Integer> keyOffsets = getMetricKeyOffsets(headers, metricKeys);
            String line = "";
            while((line = reader.readLine()) != null){
                List<String> metrics = parseALine(line);
                extractMetricKeyValue(metricsMap, headers, keyOffsets, metrics,metricPrefix);
            }

        } catch (IOException e) {
            logger.error("Error in extracting metrics " + e);
        }
        return metricsMap;
    }

    private void extractMetricKeyValue(Map<String, String> metricsMap, List<String> headers, List<Integer> keyOffsets, List<String> metrics, String metricPrefix) {
        String metricKeyPrefix = buildMetricKeyPrefix(metrics,keyOffsets,metricPrefix);
        String[] metricTokens = metrics.toArray(new String[metrics.size()]);
        for(int tokenIndex=0; tokenIndex < metricTokens.length; tokenIndex++){
            if(keyOffsets.isEmpty()){
               logger.error("Metric Key was not found. Please check the configuration.");
                break;
            }
            if(!keyOffsets.contains(tokenIndex)){
                String metricKey = metricKeyPrefix + headers.get(tokenIndex);
                String metricValue = metricTokens[tokenIndex];
                if(metricKey.endsWith("_percent")) {
                    if (metricValue != null && metricValue.length() > 0 && metricValue.charAt(metricValue.length()-1)=='%') {
                        metricValue = String.valueOf(Math.round(Double.parseDouble(metricValue.substring(0, metricValue.length()-1))));
                    }
                }
                else if(metricKey.endsWith("health")) {
                    metricValue = defineIndexHealth(metricValue);
                }
                else if(metricKey.endsWith("status")) {
                    metricValue = defineIndexStatus(metricValue);
                }
                // deal with Cluster Status Metric
                else if(metricKey.endsWith("st")) {
                    metricKey = metricKey.substring(0, metricKey.length() - 2) + "status";
                    metricValue = defineIndexHealth(metricValue);
                }
                if(isMetricValueValid(metricValue)) {
                    metricsMap.put(metricKey, metricValue);
                }
                else{
                    logger.warn("Invalid Metric with MetricKey::" + metricKey + " and MetricValue::" + metricValue);
                }
            }
        }
    }

    private boolean isMetricValueValid(Object metricValue) {
        if(metricValue == null){
            return false;
        }
        if(metricValue instanceof String){
            try {
                Double.valueOf((String) metricValue);
                return true;
            }
            catch(NumberFormatException nfe){
                //logger.warn("Metric Value is invalid " + nfe);
            }
        }
        else if(metricValue instanceof Number){
            return true;
        }
        return false;
    }

    private String buildMetricKeyPrefix(List<String> tokens, List<Integer> keyOffsets,String metricPrefix) {
        StringBuilder prefixBuilder = new StringBuilder();
        for(int offset : keyOffsets){
            if (!Strings.isNullOrEmpty(metricPrefix)) {
                prefixBuilder.append(metricPrefix);
                prefixBuilder.append(METRIC_PATH_SEPARATOR);
            }
            prefixBuilder.append(tokens.get(offset));
            prefixBuilder.append(METRIC_PATH_SEPARATOR);
        }
        return prefixBuilder.toString();
    }

    private List<Integer> getMetricKeyOffsets(List<String> headers, List<String> metricKeys) {
        if(metricKeys.isEmpty()){
            return Lists.newArrayList();
        }
        List<Integer> keyOffsets = Lists.newArrayList();
        for(String key:metricKeys){
            if(headers.contains(key)){
                keyOffsets.add(headers.indexOf(key));
            }
        }
        return keyOffsets;
    }

    public List<String> parseALine(String line) {
        Splitter spaceSplitter = Splitter.on(" ")
                .omitEmptyStrings()
                .trimResults();
        List<String> tokens = Lists.newArrayList();
        for(String str : spaceSplitter.split(line)){
            tokens.add(str);
        }
        return tokens;
    }

    /**
     * Assigns an integer value to show the index health in AppDynamics
     * controller green -> 2 yellow -> 1 red -> 0
     *
     * @param health
     *            Status string (green, yellow, or red)
     * @return corresponding string value (2, 1, or 0)
     */
    private String defineIndexHealth(String health) {
        if (health.equals("green")) {
            return "2";
        } else if (health.equals("yellow")) {
            return "1";
        } else {
            return "0";
        }
    }

    /**
     * Assigns an integer value to show the index status in AppDynamics
     * controller open -> 1 close -> 0
     */
    private String defineIndexStatus(String status) {
        if (status.equals("open")) {
            return "1";
        } else {
            return "0";
        }
    }
}
