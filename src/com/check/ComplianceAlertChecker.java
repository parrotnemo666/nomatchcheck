package com.check;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import java.util.logging.Logger;

/**
 * 遵法告警檢查器
 * 功能: 檢查指定時間範圍內的記錄（僅在AI告警未觸發時執行）
 */
public class ComplianceAlertChecker {

    private static final Logger logger = Logger.getLogger(ComplianceAlertChecker.class.getName());

    private Connection conn;
    private Properties props;
    private List<String> keywords;
    private int threshold;

    /**
     * 構造函數
     * @param conn 數據庫連接
     * @param props 配置屬性
     * @param keywords 關鍵字列表
     * @param threshold 告警閾值
     */
    public ComplianceAlertChecker(Connection conn, Properties props, List<String> keywords, int threshold) {
        this.conn = conn;
        this.props = props;
        this.keywords = keywords;
        this.threshold = threshold;
    }

    /**
     * 執行檢查
     * @return 檢查結果
     */
    public CheckResult check() {
        logger.info("[遵法告警] 開始檢查");

        List<String> records = new ArrayList<>();
        int count = 0;

        try {
            // 讀取時間配置
            int time = Integer.parseInt(props.getProperty("rec.check.time"));
            String unit = props.getProperty("rec.check.unit");
            logger.info(String.format("[遵法告警] 檢查範圍: 最近 %d %s", time, unit));

            // 建立SQL查詢
            String sql = buildSqlQuery(time, unit);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, threshold);

                try (ResultSet rs = pstmt.executeQuery()) {
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
                                logger.info(String.format("[遵法告警] 發現異常 - 關鍵字:%s, UUID:%s", keyword, callUUID));
                                break;
                            }
                        }
                    }
                }
            }

            // 判斷是否需要告警
            boolean needAlert = (count >= threshold);

            if (needAlert) {
                logger.warning(String.format("[遵法告警] 觸發告警! 異常數:%d >= 閾值:%d", count, threshold));
            } else {
                logger.info(String.format("[遵法告警] 未達閾值, 異常數:%d < 閾值:%d", count, threshold));
            }

            return new CheckResult(needAlert, count, records);

        } catch (Exception e) {
            logger.severe("[遵法告警] 執行錯誤: " + e.getMessage());
            return new CheckResult(false, -1, new ArrayList<>());
        }
    }

    /**
     * 建立SQL查詢
     */
    private String buildSqlQuery(int time, String unit) {
        // 根據時間單位建立WHERE子句
        String unitStr;
        int unitStrLength;

        switch (unit.toLowerCase()) {
            case "day":
                unitStr = "DD";
                unitStrLength = 10;
                break;
            case "hour":
                unitStr = "HH";
                unitStrLength = 19;
                break;
            default:
                unitStr = "MINUTE";
                unitStrLength = 19;
                break;
        }

        String whereClause = "where icr.[Begin_Time] between (SELECT DATEADD("
                + unitStr + ",-" + time
                + ",CONVERT(DATETIME,CONVERT(VARCHAR(" + unitStrLength
                + "),GETDATE(),120)))) and GETDATE()";

        // 組合完整SQL
        return props.getProperty("sql.Select")
                + props.getProperty("sql.SelectValue")
                + props.getProperty("sql.From")
                + props.getProperty("sql.JoinREC")
                + whereClause
                + props.getProperty("sql.WhereAndREC")
                + props.getProperty("sql.Order");
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