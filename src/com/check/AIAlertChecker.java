package com.check;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * AI告警檢查器
 * 功能: 檢查當日記錄中 Reason_Memo 是否包含異常關鍵字
 */
public class AIAlertChecker {

    private static final Logger logger = Logger.getLogger(AIAlertChecker.class.getName());

    private Statement stmt;
    private Properties props;
    private List<String> keywords;
    private int threshold;

    /**
     * 構造函數
     * @param stmt 數據庫Statement
     * @param props 配置屬性
     * @param keywords 關鍵字列表
     * @param threshold 告警閾值
     */
    public AIAlertChecker(Statement stmt, Properties props, List<String> keywords, int threshold) {
        this.stmt = stmt;
        this.props = props;
        this.keywords = keywords;
        this.threshold = threshold;
    }

    /**
     * 執行檢查
     * @return 檢查結果
     */
    public CheckResult check() {
        logger.info("[AI告警] 開始檢查");

        List<String> records = new ArrayList<>();
        int count = 0;

        try {
            // 執行SQL查詢
            String sql = props.getProperty("sql.ResultSet");
            ResultSet rs = stmt.executeQuery(sql);

            // 逐筆檢查關鍵字
            while (rs.next()) {
                String callUUID = rs.getString("CallUUID");
                String flowId = rs.getString("Flow_id");
                String reasonMemo = rs.getString("Reason_Memo");

                // 檢查是否包含任一關鍵字
                for (String keyword : keywords) {
                    if (reasonMemo != null && reasonMemo.contains(keyword)) {
                        count++;
                        String record = callUUID + "," + flowId + "," + reasonMemo;
                        records.add(record);
                        logger.info(String.format("[AI告警] 發現異常 - 關鍵字:%s, UUID:%s", keyword, callUUID));
                        break; // 找到一個就跳出
                    }
                }
            }

            // 判斷是否需要告警
            boolean needAlert = (count >= threshold);

            if (needAlert) {
                logger.warning(String.format("[AI告警] 觸發告警! 異常數:%d >= 閾值:%d", count, threshold));
            } else {
                logger.info(String.format("[AI告警] 未達閾值, 異常數:%d < 閾值:%d", count, threshold));
            }

            return new CheckResult(needAlert, count, records);

        } catch (SQLException e) {
            logger.severe("[AI告警] 執行錯誤: " + e.getMessage());
            return new CheckResult(false, -1, new ArrayList<>());
        }
    }

    /**
     * 檢查結果類
     */
    public static class CheckResult {
        private boolean needAlert;
        private int count;
        private List<String> records;

        public CheckResult(boolean needAlert, int count, List<String> records) {
            this.needAlert = needAlert;
            this.count = count;
            this.records = records;
        }

        public boolean isNeedAlert() {
            return needAlert;
        }

        public int getCount() {
            return count;
        }

        public List<String> getRecords() {
            return records;
        }
    }
}