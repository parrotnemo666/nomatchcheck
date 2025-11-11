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
 * 錄音長度檢查器
 * 功能: 檢查 Record_GVP 長度是否不足
 */
public class RecordGVPChecker {

    private static final Logger logger = Logger.getLogger(RecordGVPChecker.class.getName());
    private static final int DEFAULT_THRESHOLD = 5;
    private static final int DEFAULT_LENGTH_LIMIT = 83;

    private Connection connGcti;
    private Properties props;

    /**
     * 構造函數
     * @param connGcti 數據庫連接
     * @param props 配置屬性
     */
    public RecordGVPChecker(Connection connGcti, Properties props) {
        this.connGcti = connGcti;
        this.props = props;
    }

    /**
     * 執行檢查
     * @return 檢查結果
     */
    public CheckResult check() {
        logger.info("[錄音長度] 開始檢查");

        int threshold = getConfigInt("gvp.check.threshold", DEFAULT_THRESHOLD);
        int lengthLimit = getConfigInt("gvp.record.length.limit", DEFAULT_LENGTH_LIMIT);

        List<String> records = new ArrayList<>();
        int count = 0;

        try {

            String sql = "SELECT icr.CallUUID, icr.Flow_ID, icr.Record_GVP\n" +
                    "FROM [dbo].[IVR_Call_Records] AS icr\n" +
                    "INNER JOIN [dbo].[IVR_Flow_Setting] AS ifs ON icr.Flow_ID = ifs.Flow_ID\n" +
                    "WHERE CAST(icr.Begin_Time AS DATE) = CAST(GETDATE() AS DATE)\n" +
                    "AND icr.End_Time IS NOT NULL\n" +
                    "AND ifs.Flow_Type IN ('R', 'O')\n" +
                    "AND LEN(icr.Record_GVP) <= 83\n";
//            String sql = "SELECT icr.CallUUID, icr.Flow_ID, icr.Record_GVP " +
//                    "FROM [dbo].[IVR_Call_Records] as icr " +
//                    "INNER JOIN [dbo].[IVR_Flow_Setting] as ifs " +
//                    "ON icr.Flow_ID = ifs.Flow_ID " +
//                    "WHERE icr.Begin_Time between(SELECT CONVERT(DATETIME,CONVERT(VARCHAR(10),GETDATE(),120))) " +
//                    "and (SELECT DATEADD(SS,-1,DATEADD(DD,1,CONVERT(DATETIME,CONVERT(VARCHAR(10),GETDATE(),120))))) " +
//                    "AND icr.End_Time is not null " +
//                    "AND (ifs.Flow_Type = 'R' OR ifs.Flow_Type = 'O') " +
//                    "AND LEN(icr.Record_GVP) <= ?";

            try (PreparedStatement pstmt = connGcti.prepareStatement(sql)) {
                pstmt.setInt(1, lengthLimit);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        count++;
                        String callUUID = rs.getString("CallUUID");
                        String flowID = rs.getString("Flow_ID");
                        String recordGVP = rs.getString("Record_GVP");

                        String record = callUUID + "," + flowID + ",Record_GVP長度不足:" + recordGVP;
                        records.add(record);

                        logger.info(String.format("[錄音長度] 發現異常 - UUID:%s, Flow_ID:%s", callUUID, flowID));
                    }
                }
            }

            boolean needAlert = (count >= threshold);

            if (needAlert) {
                logger.warning(String.format("[錄音長度] 觸發告警! 異常數:%d >= 閾值:%d", count, threshold));
            } else {
                logger.info(String.format("[錄音長度] 未達閾值, 異常數:%d < 閾值:%d", count, threshold));
            }

            return new CheckResult(records, count, needAlert);

        } catch (SQLException e) {
            logger.severe("[錄音長度] 執行錯誤: " + e.getMessage());
            return new CheckResult(new ArrayList<>(), 0, false);
        }
    }

    /**
     * 讀取配置整數值
     */
    private int getConfigInt(String key, int defaultValue) {
        if (props != null) {
            String value = props.getProperty(key);
            if (value != null) {
                try {
                    return Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    logger.warning(String.format("[錄音長度] 配置 %s 格式錯誤，使用默認值: %d", key, defaultValue));
                }
            }
        }
        return defaultValue;
    }

    /**
     * 檢查結果類
     */
    public static class CheckResult {
        private List<String> records;
        private int count;
        private boolean needAlert;

        public CheckResult(List<String> records, int count, boolean needAlert) {
            this.records = records;
            this.count = count;
            this.needAlert = needAlert;
        }

        public List<String> getRecords() {
            return records;
        }

        public int getCount() {
            return count;
        }

        public boolean isNeedAlert() {
            return needAlert;
        }
    }
}