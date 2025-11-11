package com.check;

import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import java.util.logging.Logger;

/**
 * 告警通知器
 * 功能: 負責將告警訊息插入到 AG_SEND_MESS_DATA 表格
 */
public class AlertNotifier {

    private static final Logger logger = Logger.getLogger(AlertNotifier.class.getName());

    private Statement stmtOra;
    private List<String> phoneList;
    private List<String> emailList;

    /**
     * 構造函數
     * @param stmtOra Oracle Statement
     * @param phoneList 簡訊接收列表
     * @param emailList 郵件接收列表
     */
    public AlertNotifier(Statement stmtOra, List<String> phoneList, List<String> emailList) {
        this.stmtOra = stmtOra;
        this.phoneList = phoneList;
        this.emailList = emailList;
    }

    /**
     * 發送AI/遵法告警
     * @param records 異常記錄列表
     * @param allowable 閾值
     */
    public void sendAIAlert(List<String> records, int allowable) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currdate = sdf.format(new Date());

        logger.info("[告警通知] 開始發送 AI流程異常告警");

        String emailSubject = "csr_voice AI流程異常告警 " + currdate;
        String msgContent = "csr_voice AI流程異常告警 " + currdate;

        // 組合告警內容
        StringBuilder sb = new StringBuilder();
        for (String record : records) {
            sb.append(record).append("; ");
        }
        String emailContent = sb.toString();

        try {
            // 發送簡訊
            int smsCount = 0;
            for (String phoneNum : phoneList) {
                String sql = getSendPhoneSql(phoneNum, msgContent);
                int result = stmtOra.executeUpdate(sql);
                if (result == 1) smsCount++;
            }
            logger.info(String.format("[告警通知] 簡訊發送: %d 筆成功", smsCount));

            // 發送郵件
            int emailCount = 0;
            for (String email : emailList) {
                String sql = getSendEmailSql(email, emailSubject, emailContent);
                int result = stmtOra.executeUpdate(sql);
                if (result == 1) emailCount++;
            }
            logger.info(String.format("[告警通知] 郵件發送: %d 筆成功", emailCount));

        } catch (SQLException e) {
            logger.severe("[告警通知] 發送失敗: " + e.getMessage());
        }
    }

    /**
     * 發送錄音長度告警
     * @param records 異常記錄列表
     */
    public void sendGVPAlert(List<String> records) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currdate = sdf.format(new Date());

        logger.info("[告警通知] 開始發送 音檔調閱異常告警");

        String emailSubject = "CSR_AI音檔無法調閱 " + currdate;
        String msgContent = "CSR_AI音檔無法調閱 " + currdate;
        String emailContent = "CSR_AI音檔無法調閱\nivr_call_records.record_gvp檔名未更新\n" +
                "發生時間: " + currdate + "\n異常記錄數量: " + records.size() + " 筆";

        try {
            // 發送簡訊
            int smsCount = 0;
            for (String phoneNum : phoneList) {
                String sql = getSendPhoneSql(phoneNum, msgContent);
                int result = stmtOra.executeUpdate(sql);
                if (result == 1) smsCount++;
            }
            logger.info(String.format("[告警通知] 簡訊發送: %d 筆成功", smsCount));

            // 發送郵件
            int emailCount = 0;
            for (String email : emailList) {
                String sql = getSendEmailSql(email, emailSubject, emailContent);
                int result = stmtOra.executeUpdate(sql);
                if (result == 1) emailCount++;
            }
            logger.info(String.format("[告警通知] 郵件發送: %d 筆成功", emailCount));

        } catch (SQLException e) {
            logger.severe("[告警通知] 發送失敗: " + e.getMessage());
        }
    }

    /**
     * 生成簡訊SQL
     */
    private String getSendPhoneSql(String phone, String content) {
        String escaped = content.replace("'", "''");
        return "INSERT INTO AG_SEND_MESS_DATA (" +
                "IS_SEND,IS_REVIEW,SEND_TO_REVIEW,MODIFY_ID,CREATE_ID,IDENTITY_FIELD," +
                "MESS_BUS_TYPE, MESS_SEND_TYPE, MESS_TYPE_CODE, MESS_ITEM_CODE, " +
                "CONTENT, SEND_TO, CREATE_NAME, CUST_ID" +
                ") VALUES('W','N','N','00000','00000',SQ_SEND_MESS_DATA.NEXTVAL,'2','1','W', 'W12', '" +
                escaped + "','" + phone + "','GVP','')";
    }

    /**
     * 生成郵件SQL
     */
    private String getSendEmailSql(String email, String subject, String content) {
        String escapedSubject = subject.replace("'", "''");
        String escapedContent = content.replace("'", "''");
        return "INSERT INTO AG_SEND_MESS_DATA (" +
                "IS_SEND,IS_REVIEW,SEND_TO_REVIEW,MODIFY_ID,CREATE_ID,IDENTITY_FIELD," +
                "MESS_BUS_TYPE, MESS_SEND_TYPE, MESS_TYPE_CODE, MESS_ITEM_CODE, " +
                "SUBJECT, CONTENT, SEND_TO, CREATE_NAME, CUST_ID" +
                ") VALUES('W','N','N','00000','00000',SQ_SEND_MESS_DATA.NEXTVAL,'2','2','W', 'W12', '" +
                escapedSubject + "','" + escapedContent + "','" + email + "','GVP','')";
    }
}