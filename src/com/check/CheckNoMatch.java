package com.check;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import java.util.logging.Logger;
//import org.apache.logging.log4j.core.config.Configurator;

/**
 * CSR - AI告警程式 (簡化重構版)
 *
 * 流程:
 * 1. AI告警檢查 (必定執行)
 * 2. 遵法告警檢查 (僅在AI未觸發時執行)
 * 3. 錄音長度檢查 (獨立執行)
 */
public class CheckNoMatch {

    private static final Logger logger = Logger.getLogger(CheckNoMatch.class.getName());

    /**
     * 屬性文件加載器
     */
    private static class LoadProperties {
        public static Properties load(String path) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(path)) {
                props.load(fis);
                logger.info("配置文件加載成功");
            } catch (FileNotFoundException e) {
                logger.severe("配置文件不存在: " + path + ", 錯誤: " + e.getMessage());
            } catch (IOException e) {
                logger.severe("配置文件讀取錯誤: " + e.getMessage());
            }
            return props;
        }
    }

    /**
     * 解密配置
     */
    private static String decode(String key) {
        String secretKey = "Hualiteq123$";
        try {
            return HQ_AESEncryption.decrypt(key, secretKey);
        } catch (Exception e) {
            logger.fine("解密失敗，使用原始值: " + e.getMessage());
            return key;
        }
    }

    /**
     * 主程序
     */
    public static void main(String[] args) {

        logger.info("========================================");
        logger.info("CSR - AI告警程式 v2.0 啟動");
        logger.info("========================================");

        // ========== 步驟1: 載入配置 ==========
        Properties props = LoadProperties.load("C:\\WorkSpace\\check-nomatch-ai-csr1106\\src\\application.properties");
//        Properties props = LoadProperties.load("C:\WorkSpace\check-nomatch-ai-csr1106\src\application.properties");
//        Configurator.initialize(null, "/gcti/checknomatchtest/log4j2.xml");

        List<String> keywords = Arrays.asList(props.getProperty("check.keyword").split(","));
        int allowable = Integer.parseInt(props.getProperty("check.allow"));
        List<String> phoneList = Arrays.asList(props.getProperty("phone.list").split(","));
        List<String> emailList = Arrays.asList(props.getProperty("email.list").split(","));

        logger.info(String.format("關鍵字: %s, 閾值: %d", keywords, allowable));

        // 解密數據庫連接
        String gctiSql = decode(props.getProperty("link.config1"));
        String gctiUsername = decode(props.getProperty("link.config2"));
        String gctiPassword = decode(props.getProperty("link.config3"));

        String oracleSql = decode(props.getProperty("link.config21"));
        String oracleUsername = decode(props.getProperty("link.config22"));
        String oraclePassword = decode(props.getProperty("link.config23"));

        // ========== 步驟2: 建立連接並執行檢查 ==========
        try (
                Connection connGcti = DriverManager.getConnection(gctiSql, gctiUsername, gctiPassword);
                Connection connOra = DriverManager.getConnection(oracleSql, oracleUsername, oraclePassword);
                Statement stmtGcti = connGcti.createStatement();
                Statement stmtOra = connOra.createStatement()
        ) {
            logger.info("資料庫連接成功");
            logger.info("========================================");

            // 創建告警通知器
            AlertNotifier notifier = new AlertNotifier(stmtOra, phoneList, emailList);

            // ========== 階段1: AI告警檢查 ==========
            logger.info("階段1: AI告警檢查");
            AIAlertChecker aiChecker = new AIAlertChecker(stmtGcti, props, keywords, allowable);
            AIAlertChecker.CheckResult aiResult = aiChecker.check();

            boolean isSend = false;
            if (aiResult.isNeedAlert()) {
                notifier.sendAIAlert(aiResult.getRecords(), allowable);
                isSend = true;
            }

            logger.info("----------------------------------------");

            // ========== 階段2: 遵法告警檢查 ==========
            if (!isSend) {
                logger.info("階段2: 遵法告警檢查");
                ComplianceAlertChecker complianceChecker =
                        new ComplianceAlertChecker(connGcti, props, keywords, allowable);
                ComplianceAlertChecker.CheckResult complianceResult = complianceChecker.check();

                if (complianceResult.isNeedAlert()) {
                    notifier.sendAIAlert(complianceResult.getRecords(), allowable);
                }
            } else {
                logger.info("階段2: 遵法告警檢查 - 已跳過 (AI已觸發)");
            }

            logger.info("----------------------------------------");

            // ========== 階段3: 錄音長度檢查 ==========
            logger.info("階段3: 錄音長度檢查");
            RecordGVPChecker gvpChecker = new RecordGVPChecker(connGcti, props);
            RecordGVPChecker.CheckResult gvpResult = gvpChecker.check();

            if (gvpResult.isNeedAlert()) {
                notifier.sendGVPAlert(gvpResult.getRecords());
            }

            logger.info("========================================");

        } catch (Exception e) {
            logger.severe("程式執行錯誤: " + e.getMessage());
        }

        logger.info("CSR - AI告警程式執行完畢");
        logger.info("========================================");
    }
}


