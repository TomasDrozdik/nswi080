import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import javax.jms.*;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.w3c.dom.Text;

public class Client {
	
	/****	CONSTANTS	****/
	
	// name of the property specifying client's name
	public static final String CLIENT_NAME_PROPERTY = "clientName";

	// name of the property specifying client's name
	public static final String ACCOUNT_NUMBER_PROPERTY = "accountNumber";

	// name of the property specifying client's name
	public static final String GOODS_NAME_PROPERTY = "goodsName";

	// name of the property specifying client's name
	public static final String GOODS_PRICE_PROPERTY = "goodsPrice";

	// name of the seller response status field
	public static final String TRANSACTION_STATE_PROPERTY = "transactionStateProperty";

	public static final String TRANSACTION_ERROR_PROPERTY = "transactionErrorProperty";
	public static final String TRANSACTION_ERROR_INSUFFICIENT_FUNDS = "Insufficient funds.";
	public static final String TRANSACTION_ERROR_NO_GOODS = "No such goods found.";

	// values for seller response status field
	public static final int TRANSACTION_STATE_ACCEPT = 1;
	public static final int TRANSACTION_STATE_DENY = 0;

	// text of the TextMessage that indicates a will to buy
	public static final String SALE_REQUEST_MSG = "SALE_REQUEST";

	// text of the TextMessage that is a response to buy request
	public static final String SALE_DETAILS_MSG = "SALE_DETAILS";

	// text of the TextMessage that is a confirmation of a sale
	public static final String SALE_CONFIRMATION_MSG = "SALE_CONFIRMATION";

	// name of the topic for publishing offers
	public static final String OFFER_TOPIC = "Offers";

	/****	PRIVATE VARIABLES	****/

	private final Object lock = new Object();
	
	// client's unique name
	private String clientName;

	// client's account number
	private int accountNumber;
	
	// offered goods, mapped by name
	private Map<String, Goods> offeredGoods = new HashMap<String, Goods>();
	
	// available goods, mapped by seller's name 
	private Map<String, List<Goods>> availableGoods = new HashMap<String, List<Goods>>();
	
	// reserved goods, mapped by name of the goods
	private Map<String, Goods> reservedGoods = new HashMap<String, Goods>();
	
	// buyer's names, mapped by their account numbers
	private Map<Integer, String> reserverAccounts = new HashMap<Integer, String>();
	
	// buyer's reply destinations, mapped by their names
	private Map<String, Destination> reserverDestinations= new HashMap<String, Destination>();
	
	// connection to the broker
	private Connection conn;
	
	// session for user-initiated synchronous messages
	private Session clientSession;

	// session for listening and reacting to asynchronous messages
	private Session eventSession;
	
	// sender for the clientSession
	private MessageProducer clientSender;
	
	// sender for the eventSession
	private MessageProducer eventSender;

	// receiver of synchronous replies
	private MessageConsumer replyReceiver;
	
	// topic to send and receiver offers
	private Topic offerTopic;
	
	// queue for sending messages to bank
	private Queue toBankQueue;

	// queue for receiving synchronous replies
	private Queue replyQueue;



	// reader of lines from stdin
	private LineNumberReader in = new LineNumberReader(new InputStreamReader(System.in));
	
	/****	PRIVATE METHODS	****/
	
	/*
	 * Constructor, stores clientName, connection and initializes maps
	 */
	private Client(String clientName, Connection conn) {
		this.clientName = clientName;
		this.conn = conn;
		
		// generate some goods
		generateGoods();
	}
	
	/*
	 * Generate goods items
	 */
	private void generateGoods() {
		Random rnd = new Random();
		for (int i = 0; i < 10; ++i) {
			String name = "";
			
			for (int j = 0; j < 4; ++j) {
				char c = (char) ('A' + rnd.nextInt('Z' - 'A'));
				name += c;
			}
			
			offeredGoods.put(name, new Goods(name, rnd.nextInt(10000)));
		}
	}
	
	/*
	 * Set up all JMS entities, get bank account, publish first goods offer 
	 */
	private void connect() throws JMSException {
		// create two sessions - one for synchronous and one for asynchronous processing
		clientSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
		eventSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
		
		// create (unbound) senders for the sessions
		clientSender = clientSession.createProducer(null);
		eventSender = eventSession.createProducer(null);
		
		// create queue for sending messages to bank
		toBankQueue = clientSession.createQueue(Bank.BANK_QUEUE);
		// create a temporary queue for receiving messages from bank
		Queue fromBankQueue = eventSession.createTemporaryQueue();

		// temporary receiver for the first reply from bank
		// note that although the receiver is created within a different session
		// than the queue, it is OK since the queue is used only within the
		// client session for the moment
		MessageConsumer tmpBankReceiver = clientSession.createConsumer(fromBankQueue);        
		
		// start processing messages
		conn.start();
		
		// request a bank account number
		Message msg = eventSession.createTextMessage(Bank.NEW_ACCOUNT_MSG);
		msg.setStringProperty(CLIENT_NAME_PROPERTY, clientName);
		// set ReplyTo that Bank will use to send me reply and later transfer reports
		msg.setJMSReplyTo(fromBankQueue);
		clientSender.send(toBankQueue, msg);
		
		// get reply from bank and store the account number
		TextMessage reply = (TextMessage) tmpBankReceiver.receive();
		accountNumber = Integer.parseInt(reply.getText());
		System.out.println("Account number: " + accountNumber);
		
		// close the temporary receiver
		tmpBankReceiver.close();
		
		// temporarily stop processing messages to finish initialization
		conn.stop();
		
		/* Processing bank reports */
		
		// create consumer of bank reports (from the fromBankQueue) on the event session
		MessageConsumer bankReceiver = eventSession.createConsumer(fromBankQueue);
		
		// set asynchronous listener for reports, using anonymous MessageListener
		// which just calls our designated method in its onMessage method
		bankReceiver.setMessageListener(new MessageListener() {
			@Override
			public void onMessage(Message msg) {
				try {
					processBankReport(msg);
				} catch (JMSException e) {
					e.printStackTrace();
				}
			}
		});

		/* Step 1: Processing offers */
		
		// create a topic both for publishing and receiving offers
		// hint: Sessions have a createTopic() method
		offerTopic = clientSession.createTopic(OFFER_TOPIC);
		
		// create a consumer of offers from the topic using the event session
		MessageConsumer offerConsumer = eventSession.createConsumer(offerTopic);
		
		// set asynchronous listener for offers (see above how it can be done)
		// which should call processOffer()
		offerConsumer.setMessageListener(new MessageListener() {
			@Override
			public void onMessage(Message msg) {
				try {
					processOffer(msg);
				} catch (JMSException e) {
					e.printStackTrace();
				}

			}
		});
		
		/* Step 2: Processing sale requests */
		
		// create a queue for receiving sale requests (hint: Session has createQueue() method)
		// note that Session's createTemporaryQueue() is not usable here, the queue must have a name
		// that others will be able to determine from clientName (such as clientName + "SaleQueue")
		Queue saleRequestQueue = eventSession.createQueue(clientName + "-SaleQueue");
		
		// create consumer of sale requests on the event session
		MessageConsumer saleRequestConsumer = eventSession.createConsumer(saleRequestQueue);
		    
		// set asynchronous listener for sale requests (see above how it can be done)
		// which should call processSale()
		saleRequestConsumer.setMessageListener(new MessageListener() {
			@Override
			public void onMessage(Message msg) {
				try {
					processSale(msg);
				} catch (JMSException e) {
					e.printStackTrace();
				}

			}
		});

		// create temporary queue for synchronous replies
		replyQueue = clientSession.createTemporaryQueue();
		
		// create synchronous receiver of the replies
		replyReceiver = clientSession.createConsumer(replyQueue);
		
		// restart message processing
		conn.start();
		
		// send list of offered goods
		publishGoodsList(clientSender, clientSession);
	}

	/*
	 * Publish a list of offered goods
	 * Parameter is an (unbound) sender that fits into current session
	 * Sometimes we publish the list on user's request, sometimes we react to an event
	 */
	private void publishGoodsList(MessageProducer sender, Session session) throws JMSException {
		// create a message (of appropriate type) holding the list of offered goods
		// which can be created like this: new ArrayList<Goods>(offeredGoods.values())
		ObjectMessage message = session.createObjectMessage();
        synchronized (lock) {
			ArrayList<Goods> goods = new ArrayList<Goods>(offeredGoods.values());
			message.setObject(goods);
		}

		// don't forget to include the clientName in the message so other clients know
		// who is sending the offer - see how connect() does it when sending message to bank
		message.setStringProperty(CLIENT_NAME_PROPERTY, clientName);
		
		// send the message using the sender passed as parameter 
		sender.send(offerTopic, message);
	}
	
	/*
	 * Send empty offer and disconnect from the broker 
	 */
	private void disconnect() throws JMSException {
		// delete all offered goods
		offeredGoods.clear();
		
		// send the empty list to indicate client quit
		publishGoodsList(clientSender, clientSession);
		
		// close the connection to broker
		conn.close();
	}
	
	/*
	 * Print known goods that are offered by other clients
	 */
	private void list() {
	    synchronized (lock) {
			System.out.println("Available goods (name: price):");
			// iterate over sellers
			for (String sellerName : availableGoods.keySet()) {
				System.out.println("From " + sellerName);
				// iterate over goods offered by a seller
				for (Goods g : availableGoods.get(sellerName)) {
					System.out.println("  " + g);
				}
			}
		}
	}
	
	/*
	 * Main interactive user loop
	 */
	private void loop() throws IOException, JMSException {
		// first connect to broker and setup everything
		connect();
		
		loop:
		while (true) {
			System.out.println("\nAvailable commands (type and press enter):");
			System.out.println(" l - list available goods");
			System.out.println(" p - publish list of offered goods");
			System.out.println(" b - buy goods");
			System.out.println(" i - get account balance");
			System.out.println(" q - quit");
			// read first character
			int c = in.read();
			// throw away rest of the buffered line
			while (in.ready()) in.read();
			switch (c) {
				case 'q':
					disconnect();
					break loop;
				case 'b':
					buy();
					break;
				case 'l':
					list();
					break;
				case 'p':
					publishGoodsList(clientSender, clientSession);
					System.out.println("List of offers published");
					break;
				case 'i':
					getAccountBalance();
				case '\n':
				default:
					break;
			}
		}
	}
	
	/*
	 * Perform buying of goods
	 */
	private void buy() throws IOException, JMSException {
		// get information from the user
		System.out.println("Enter seller name:");
		String sellerName = in.readLine();
		System.out.println("Enter goods name:");
		String goodsName = in.readLine();

		List<Goods> sellerGoods;
		synchronized (lock) {
			sellerGoods = availableGoods.get(sellerName);
		}

		// check if the seller exists
		if (sellerGoods == null) {
			System.out.println("Seller does not exist: " + sellerName);
			return;
		}
		int[] listedPrices = sellerGoods.stream()
			.filter(goods -> goodsName.equals(goods.name))
			.mapToInt(goods -> goods.price)
			.toArray();

		System.out.printf("Goods \"%s\" from user \"%s\" is available for: ", goodsName, sellerName);
		for (int listedPrice : listedPrices) {
			System.out.printf("$%d ", listedPrice);
		}
		System.out.println();

		// First consider what message types clients will use for communicating a sale
		// we will need to transfer multiple values (of String and int) in each message 
		// MapMessage? ObjectMessage? TextMessage with extra properties?
		
		/* Step 1: send a message to the seller requesting the goods */
		
		// create local reference to the seller's queue
		// similar to Step 2 in connect() but using sellerName instead of clientName
		Queue sellerQueue = clientSession.createQueue(sellerName + "-SaleQueue");
		
		// create message requesting sale of the goods
		// includes: clientName, goodsName, accountNumber
		// also include reply destination that the other client will use to send reply (replyQueue)
		// how? see how connect() uses SetJMSReplyTo() 
		Message msg = eventSession.createTextMessage(SALE_REQUEST_MSG);
		msg.setStringProperty(CLIENT_NAME_PROPERTY, clientName);
		msg.setStringProperty(GOODS_NAME_PROPERTY, goodsName);
		msg.setIntProperty(ACCOUNT_NUMBER_PROPERTY, accountNumber);

		msg.setJMSReplyTo(replyQueue);
					
		// send the message (with clientSender)
		clientSender.send(sellerQueue, msg);
		
		/* Step 2: get seller's response and process it */
		
		// receive the reply (synchronously, using replyReceiver)
		msg = replyReceiver.receive();
		
		// parse the reply (depends on your selected message format)
		// distinguish between "sell denied" and "sell accepted" message
		// in case of "denied", report to user and return from this method
		// in case of "accepted"
		// - obtain seller's account number and price to pay
		int price = 0;
		int sellerAccount = 0;

		if (msg instanceof TextMessage && ((TextMessage)msg).getText().equals(SALE_DETAILS_MSG)) {
			TextMessage saleDetails = (TextMessage)msg;
			int state = saleDetails.getIntProperty(TRANSACTION_STATE_PROPERTY);
			switch (state) {
				case TRANSACTION_STATE_ACCEPT:
					sellerAccount = saleDetails.getIntProperty(ACCOUNT_NUMBER_PROPERTY);
					price = saleDetails.getIntProperty(GOODS_PRICE_PROPERTY);
					break;
				case TRANSACTION_STATE_DENY:
				    String error = saleDetails.getStringProperty(TRANSACTION_ERROR_PROPERTY);
				    System.out.println("Seller responded with error: " + error);
				    return;
			}
		} else {
			System.out.println("Seller responded with a message of unknown type:\n" + msg);
			return;
		}
		
		// verify price tag
		final int finalPrice = price;
		if (IntStream.of(listedPrices).noneMatch(x -> x == finalPrice)) {
			System.out.printf("Seller \"%s\" offers \"%s\" for previously unknown price: \"%s\"\nDo you wish to continue? (y/N): %n",
				sellerName, goodsName, price);

			int c = in.read();
			// throw away rest of the buffered line
			while (in.ready()) in.read();
			switch (Character.toUpperCase((char)c)) {
				case 'Y':
					break;
				default:
					System.out.println("Transaction declined.");
					return;
			}
		}

		/* Step 3: send message to bank requesting money transfer */
		
		// create message ordering the bank to send money to seller
		MapMessage bankMsg = clientSession.createMapMessage();
		bankMsg.setStringProperty(CLIENT_NAME_PROPERTY, clientName);
		bankMsg.setInt(Bank.ORDER_TYPE_KEY, Bank.ORDER_TYPE_SEND);
		bankMsg.setInt(Bank.ORDER_RECEIVER_ACC_KEY, sellerAccount);
		bankMsg.setInt(Bank.AMOUNT_KEY, price);

		System.out.println("Sending $" + price + " to account " + sellerAccount);

		// send message to bank
		clientSender.send(toBankQueue, bankMsg);

		/* Step 4: wait for seller's sale confirmation */

		// receive the confirmation, similar to Step 2
		msg = replyReceiver.receive();

		// parse message and verify it's confirmation message
		if (msg instanceof TextMessage && ((TextMessage)msg).getText().equals(SALE_CONFIRMATION_MSG)) {
			TextMessage saleConfirmation = (TextMessage) msg;
			Integer status = saleConfirmation.getIntProperty(TRANSACTION_STATE_PROPERTY);
			switch (status) {
				case TRANSACTION_STATE_ACCEPT:
					// report successful sale to the user
					System.out.printf("Sale %s[%s]:%s:%s SUCCESSFUL%n", sellerName, sellerAccount, goodsName, finalPrice);
					return;
				case TRANSACTION_STATE_DENY:
					String error = saleConfirmation.getStringProperty(TRANSACTION_ERROR_PROPERTY);
					System.out.printf("Sale %s[%s]:%s:%s - FAILED - %s%n", sellerName, sellerAccount, goodsName, finalPrice, error);
					return;
				default:
					System.out.printf("Sale confirmation message has unknown type: %d%n", status);
			}
		} else {
			System.out.println("Seller responded with a message of unknown type:\n" + msg);
		}
	}
	
	/*
	 * Process a message with goods offer
	 */
	private void processOffer(Message msg) throws JMSException {
		// parse the message, obtaining sender's name and list of offered goods
		try {
			if (msg instanceof ObjectMessage && ((ObjectMessage)msg).getObject() instanceof ArrayList) {
				ObjectMessage offersMsg = (ObjectMessage)msg;
				// should ignore messages sent from myself
				String sender = offersMsg.getStringProperty(CLIENT_NAME_PROPERTY);
				if (clientName.equals(sender)) {
					return;
				}

				synchronized (lock) {
					// store the list into availableGoods (replacing any previous offer)
					// empty list means disconnecting client, remove it from availableGoods completely
					ArrayList<Goods> items = (ArrayList<Goods>) offersMsg.getObject();
					if (items.isEmpty()) {
						availableGoods.remove(sender);
					} else {
						availableGoods.put(sender, items);
					}
				}
			} else {
				System.out.println("processOffer: Received unknown message:\n: " + msg);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Process message requesting a sale
	 */
	private void processSale(Message msg) throws JMSException {
		/* Step 1: parse the message */

		// distinguish that it's the sale request message
		TextMessage saleRequest;
		if (msg instanceof TextMessage && ((TextMessage) msg).getText().equals(SALE_REQUEST_MSG)) {
			saleRequest = (TextMessage) msg;
		} else {
			System.out.println("processSale: invalid sale request msg type:\n" + msg);
			return;
		}

		// obtain buyer's name (buyerName), goods name (goodsName) , buyer's account number (buyerAccount)
        String buyerName = saleRequest.getStringProperty(CLIENT_NAME_PROPERTY);
		String goodsName = saleRequest.getStringProperty(GOODS_NAME_PROPERTY);
		int buyerAccount = saleRequest.getIntProperty(ACCOUNT_NUMBER_PROPERTY);

		// also obtain reply destination (buyerDest)
		// how? see for example Bank.processTextMessage()
		Destination buyerDest = saleRequest.getJMSReplyTo();

		/* Step 2: decide what to do and modify data structures accordingly */
		TextMessage response = clientSession.createTextMessage(SALE_DETAILS_MSG);

        synchronized (lock) {
			// check if we still offer this goods
			Goods goods = offeredGoods.get(goodsName);
			if (goods == null) {
				response.setIntProperty(TRANSACTION_STATE_PROPERTY, TRANSACTION_STATE_DENY);
				response.setStringProperty(TRANSACTION_ERROR_PROPERTY, TRANSACTION_ERROR_NO_GOODS);
			} else {
				// if yes, we should remove it from offeredGoods and publish new list
				// also it's useful to create a list of "reserved goods" together with buyer's information
				// such as name, account number, reply destination
				offeredGoods.remove(goodsName);
				reservedGoods.put(buyerName, goods);
				reserverAccounts.put(buyerAccount, buyerName);
				reserverDestinations.put(buyerName, buyerDest);

				// Publish the goods since they changed
				publishGoodsList(eventSender, eventSession);

				response.setIntProperty(TRANSACTION_STATE_PROPERTY, TRANSACTION_STATE_ACCEPT);
				response.setIntProperty(ACCOUNT_NUMBER_PROPERTY, accountNumber);
				response.setIntProperty(GOODS_PRICE_PROPERTY, goods.price);
			}
		}

		/* Step 3: send reply message */
		
		// prepare reply message (accept or deny)
		// accept message includes: my account number (accountNumber), price (goods.price)
        clientSender.send(buyerDest, response);
	}
	
	/*
	 * Process message with (transfer) report from the bank
	 */
	private void processBankReport(Message msg) throws JMSException {
		/* Step 1: parse the message */
		
		// Bank reports are sent as MapMessage
		if (msg instanceof MapMessage) {
			MapMessage mapMsg = (MapMessage) msg;
			synchronized (lock) {
				// get report number
				int cmd = mapMsg.getInt(Bank.REPORT_TYPE_KEY);
				if (cmd == Bank.REPORT_TYPE_RECEIVED) {
					// get account number of sender and the amount of money sent
					int buyerAccount = mapMsg.getInt(Bank.REPORT_SENDER_ACC_KEY);
					int amount = mapMsg.getInt(Bank.AMOUNT_KEY);

					// match the sender account with sender
					String buyerName = reserverAccounts.get(buyerAccount);

					// match the reserved goods
					Goods g = reservedGoods.get(buyerName);

					System.out.println("Received $" + amount + " from " + buyerName);

					/* Step 2: decide what to do and modify data structures accordingly */

					// did he pay enough?
					if (amount >= g.price) {
						// get the buyer's destination
						Destination buyerDest = reserverDestinations.get(buyerName);

						// remove the reserved goods and buyer-related information
						reserverDestinations.remove(buyerName);
						reserverAccounts.remove(buyerAccount);
						reservedGoods.remove(buyerName);

						/* Step 3: send confirmation message */

						// prepare sale confirmation message
						// includes: goods name (g.name)
						TextMessage confirmationMsg = clientSession.createTextMessage(SALE_CONFIRMATION_MSG);
						confirmationMsg.setIntProperty(TRANSACTION_STATE_PROPERTY, TRANSACTION_STATE_ACCEPT);
						confirmationMsg.setStringProperty(GOODS_NAME_PROPERTY, g.name);

						// send reply (destination is buyerDest)
						clientSender.send(buyerDest, confirmationMsg);

						// TODO: send bank confirmation

					} else {
						// we don't consider this now for simplicity

						// TODO: send bank a negative confirmation

						assert (false);
					}
				} else if (cmd == Bank.REPORT_TYPE_INSUFFICIENT_FUNDS) {
					// get account number of sender and the amount of money sent
					int buyerAccount = mapMsg.getInt(Bank.REPORT_SENDER_ACC_KEY);
					int amount = mapMsg.getInt(Bank.AMOUNT_KEY);

					// match the sender account with sender
					String buyerName = reserverAccounts.get(buyerAccount);

					// match the reserved goods
					Goods g = reservedGoods.get(buyerName);

					System.out.println("Received Insufficient amount $" + amount + " from " + buyerName);

					// get the buyer's destination
					Destination buyerDest = reserverDestinations.get(buyerName);

					TextMessage denyMsg = clientSession.createTextMessage(SALE_CONFIRMATION_MSG);
					denyMsg.setIntProperty(TRANSACTION_STATE_PROPERTY, TRANSACTION_STATE_DENY);
					denyMsg.setStringProperty(GOODS_NAME_PROPERTY, g.name);
					denyMsg.setStringProperty(TRANSACTION_ERROR_PROPERTY, TRANSACTION_ERROR_INSUFFICIENT_FUNDS);

					clientSender.send(buyerDest, denyMsg);
				} else {
					System.out.println("Received unknown MapMessage:\n: " + msg);
				}
			}
		} else {
			System.out.println("Received unknown message:\n: " + msg);
		}
	}

	private void getAccountBalance() throws JMSException {
		Queue fromBankQueue = clientSession.createTemporaryQueue();
		MessageConsumer tmpBankReceiver = clientSession.createConsumer(fromBankQueue);

		TextMessage query = clientSession.createTextMessage(Bank.CHECK_BALANCE_QUERY_MSG);
		query.setIntProperty(ACCOUNT_NUMBER_PROPERTY, accountNumber);

		query.setJMSReplyTo(fromBankQueue);
		clientSender.send(toBankQueue, query);

		// get reply from bank and store the account number
		Message msg = tmpBankReceiver.receive();

		if (msg instanceof TextMessage && Bank.CHECK_BALANCE_REPLY_MSG.equals(((TextMessage)msg).getText())) {
			TextMessage reply = (TextMessage) msg;
			int balance = reply.getIntProperty(Bank.ACCOUNT_BALANCE_PROPERTY);
			System.out.printf("Current account [%s] balance: %d%n", accountNumber, balance);
		} else {
			System.out.println("getAccountBalance: Received unknown message:\n: " + msg);
		}
	}
	
	/**** PUBLIC METHODS ****/
	
	/*
	 * Main method, creates client instance and runs its loop
	 */
	public static void main(String[] args) {

		if (args.length != 1) {
			System.err.println("Usage: ./client <clientName>");
			return;
		}
		
		// create connection to the broker.
		try (ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
				Connection connection = connectionFactory.createConnection()) {
			// create instance of the client
			Client client = new Client(args[0], connection);
			
			// perform client loop
			client.loop();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
