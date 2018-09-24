package com.org.example.mqtt;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PreDestroy;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class Client implements MqttCallback {

    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());
    
    MqttClient client;

    MqttConnectOptions options;
    
//    final String url;
//    
//    final Resource caFile;
//
//    final String userName;
//    
//    final char[] password;
    
    @Autowired
    MqttProperties prop;
    
    
    public Client() {
    }
    
    void subscribe() throws Exception {
        client.connect(options);
        client.subscribe("test");
    }

    Optional<KeyStore> loadKeyStore() {
        X509Certificate cert;

        if (prop.getCaFile() == null) {
            return Optional.empty();
        }
        PEMParser parser = null;
        try (InputStream is = prop.getCaFile().getInputStream()) {
            InputStreamReader isr = new InputStreamReader(is);
            parser = new PEMParser(isr);
            X509CertificateHolder holder = (X509CertificateHolder) parser.readObject();
            cert = new JcaX509CertificateConverter().getCertificate(holder);
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", cert);
            return Optional.of(keyStore);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "failed load", e);
            return Optional.empty();
        }
        finally {
            Optional.ofNullable(parser).ifPresent(t -> {
                try {
                    t.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    Optional<TrustManager[]> initTrustManagers() {
        return loadKeyStore().map(keyStore -> {
            try {
                Security.addProvider(new BouncyCastleProvider());
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(keyStore);
                return tmf.getTrustManagers();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "failed load", e);
                return null;
            }
        });
    }
    
    void createMqttConnectOptions() {
        Optional<SSLContext> context = initTrustManagers().map(trustManagers -> {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(null, trustManagers, new SecureRandom());
                return sslContext;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "failed load", e);
                return null;
            }
        });

        options = new MqttConnectOptions();
        options.setUserName(prop.getUsername());
        options.setPassword(prop.getPassword().toCharArray());
        context.ifPresent(sslContext -> options.setSocketFactory(sslContext.getSocketFactory()));
    }
    
    @Bean
    MqttClient createMqttClient() throws MqttException {
        client = new MqttClient(prop.getUrl(), MqttClient.generateClientId(), new MemoryPersistence());
        client.setCallback(this);
        createMqttConnectOptions();
        
        try {
            subscribe();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return client;
    }
    
//    @PostConstruct
//    public void afterPropertiesSet() throws Exception {        
//        createMqttConnectOptions();
//        createMqttClient();
//        subscribe();
//    }

    @PreDestroy
    public void destroy() throws Exception {
        if (null == client) return;
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } finally {
            client.close();
        }
    }
    
    @Override
    public void connectionLost(Throwable cause) {
        LOGGER.log(Level.INFO, "connectionLost", cause);
        try {
            subscribe();
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "connectionLost", e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        LOGGER.log(Level.INFO, String.format("%s: %s", topic, message));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        LOGGER.log(Level.INFO, token.toString());
    }

    public MqttClient getClient() {
        return client;
    }
}