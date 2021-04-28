import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

public class ExampleProducer {
	
	public static void main(String[] args) {
		
		// Create connection to the broker.
		// Note that the factory is usually obtained from JNDI, this method is ActiveMQ-specific
		// used here for simplicity
		//try (ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://lab.d3s.mff.cuni.cz:5000", "labUser", "sieb5w9");
		try (ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
				Connection connection = connectionFactory.createConnection()){
			connection.start();
			
			// Create a non-transacted, auto-acknowledged session
			Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			
			// Create a queue, name must match the queue created by consumer
			// Note that this is also provider-specific and should be obtained from JNDI
			Queue queue1 = session.createQueue("ExampleQueue1");

			// Create a producer for the queue
			MessageProducer producer1 = session.createProducer(queue1);

			// Create a message
			Message message1 = session.createTextMessage("ping");

			// Send the message
			producer1.send(message1);
			
			// Repeat all this with another queue
			Queue queue2 = session.createQueue("ExampleQueue2");
			MessageProducer producer2 = session.createProducer(queue2);
			Message message2 = session.createTextMessage("ping");
			producer2.send(message2);

			// Lab assignment Repeate with another queue
			//Queue queue3 = session.createQueue("LabQueue");
			//MessageProducer producer3 = session.createProducer(queue3);		
			//Message message3 = session.createTextMessage("totalyUniqueNickName");
			//producer3.send(message3);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
