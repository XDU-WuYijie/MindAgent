package com.mindagent.agent.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class QueryRewriteService {

    public QueryRewriteResult rewrite(String query, QueryType queryType) {
        String safeQuery = query == null ? "" : query.trim();
        Set<String> keywords = new LinkedHashSet<>();
        if (!safeQuery.isBlank()) {
            keywords.add(safeQuery);
        }

        String normalized = safeQuery.toLowerCase(Locale.ROOT);
        if (queryType == QueryType.APPOINTMENT_PROCESS) {
            keywords.add("心理咨询预约");
            keywords.add("预约流程");
            if (normalized.contains("取消") || normalized.contains("改期")) {
                keywords.add("取消预约");
                keywords.add("改期");
            }
            if (normalized.contains("状态") || normalized.contains("记录")) {
                keywords.add("预约状态");
                keywords.add("我的预约");
            }
            if (normalized.contains("通知") || normalized.contains("提醒")) {
                keywords.add("预约通知");
            }
        } else if (queryType == QueryType.APPOINTMENT_ACTION) {
            keywords.add("可预约时间");
            keywords.add("创建预约");
            keywords.add("我的预约");
            if (normalized.contains("取消")) {
                keywords.add("取消预约");
            }
        } else if (queryType == QueryType.PSYCHOLOGY_KNOWLEDGE) {
            keywords.add("心理健康");
            if (normalized.contains("焦虑")) {
                keywords.add("考试焦虑");
                keywords.add("情绪调节");
            }
            if (normalized.contains("睡") || normalized.contains("失眠")) {
                keywords.add("睡眠问题");
            }
            if (normalized.contains("人际") || normalized.contains("同学") || normalized.contains("室友")) {
                keywords.add("人际关系");
            }
            if (normalized.contains("压力")) {
                keywords.add("学习压力");
            }
        }

        List<String> ordered = new ArrayList<>(keywords);
        String rewritten = String.join("；", ordered);
        return new QueryRewriteResult(safeQuery, rewritten, ordered);
    }
}
