package com.example.awsiotdataloggingjavatest;

import com.amazonaws.regions.Regions;

public class AWSSettings {
    public static final String KEY_STORE_NAME = "android_test1.jks";
    public static final String KEY_STORE_PASSWORD = "android_test_pass";
    public static final String CERT_ID = "android_test_certid";
    public static final String AWS_IOT_THING_NAME = "android-test1";
    public static final String AWS_IOT_ENDPOINT = "*************-ats.iot.ap-northeast-1.amazonaws.com";
    public static final String CERT_FILE = "certificate.pem.crt";
    public static final String PRIVATE_KEY_FILE = "private.pem.key";
    public static final String AWS_IOT_TOPIC_NAME = "aosp_topic";
    public static final Regions REGION = Regions.AP_NORTHEAST_1;
}
