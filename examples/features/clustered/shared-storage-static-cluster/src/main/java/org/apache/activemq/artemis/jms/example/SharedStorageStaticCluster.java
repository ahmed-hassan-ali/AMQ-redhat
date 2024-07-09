/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.jms.example;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import org.apache.activemq.artemis.api.core.client.FailoverEventListener;
import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.util.ServerUtil;

/**
 * This example demonstrates how you can listen on failover event on the client side
 * <p>
 * In this example there are two nodes running in a cluster, both server will be running for start,
 * but after a while the first server will crash. This will trigger a fail-over event
 */
public class SharedStorageStaticCluster {

   private static Process server0;

   private static Process server1;

   private static Process server2;

   public static void main(final String[] args) throws Exception {

      for (String x : args) {
         System.out.println("::" + x);
      }
      InitialContext initialContext = null;

      Connection connection = null;

      try {
         server0 = ServerUtil.startServer(args[0], SharedStorageStaticCluster.class.getSimpleName() + "0", 0, 5000);
         server1 = ServerUtil.startServer(args[1], SharedStorageStaticCluster.class.getSimpleName() + "1", 1, 0);

         // Step 3. Look-up a JMS Connection Factory object from JNDI on server 0
         //ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://(localhost:61616,localhost:61617,localhost:61618)");
         ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("(tcp://localhost:61616,tcp://localhost:61617,tcp://localhost:61618)?ha=true&retryInterval=100&retryIntervalMultiplier=1.0&reconnectAttempts=-1");

         // Step 4. We create 1 JMS connections from the same connection factory.
         // Wait a little while to make sure broadcasts from all nodes have reached the client
         Thread.sleep(5000);
         connection = connectionFactory.createConnection();
         ((ActiveMQConnection) connection).setFailoverListener(new FailoverListenerImpl());

         // Step 5. We create JMS Sessions
         final Session sessionA = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = (Queue) sessionA.createQueue("exampleQueue");

         // Step 6. We create JMS MessageProducer objects on the sessions
         MessageProducer producerA = sessionA.createProducer(queue);

         // Step 7. We send some messages on each producer
         final int numMessages = 10;

         for (int i = 0; i < numMessages; i++) {
            TextMessage messageA = sessionA.createTextMessage("A:This is text message " + i);
            producerA.send(messageA);
            System.out.println("Sent message: " + messageA.getText());

         }

         // Step 8. We start the connection to consume messages
         connection.start();

         MessageConsumer consumer = sessionA.createConsumer(queue);

         for (int i = 0; i < numMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(2000);
            System.out.println("Got message: " + message.getText() + " from node A");
            if (i == 5) {
               ServerUtil.killServer(server0);
            }
         }

         System.out.println("receive other message from node B: " + consumer.receive(2000));

         consumer.close();

         server2 = ServerUtil.startServer(args[2], SharedStorageStaticCluster.class.getSimpleName() + "2", 2, 0);

         Thread.sleep(5000);

         server1.destroy();

         Thread.sleep(5000);

         for (int i = 0; i < numMessages; i++) {
            try {
               TextMessage messageA = sessionA.createTextMessage("A:This is text message after kill " + i);
               producerA.send(messageA);
               System.out.println("Sent message: " + messageA.getText());
            } catch (Exception e) {
               // it can happen
               e.printStackTrace();
            }
         }

         connection.start();
         consumer = sessionA.createConsumer(queue);

         for (int i = 0; i < numMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(2000);
            System.out.println("Got message: " + message.getText());
         }

         consumer.close();

      } finally {
         // Step 10. Be sure to close our resources!

         if (connection != null) {
            connection.close();
         }

         if (initialContext != null) {
            initialContext.close();
         }

         ServerUtil.killServer(server0);
         ServerUtil.killServer(server1);
      }
   }

   private static class FailoverListenerImpl implements FailoverEventListener {

      @Override
      public void failoverEvent(FailoverEventType eventType) {
         System.out.println("Failover event triggered :" + eventType.toString());
      }
   }
}
