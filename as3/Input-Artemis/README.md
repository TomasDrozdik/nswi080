# Assignment 3 : JMS

## Build and run

```
bash make
bash install

# Tui 1 : run broker
bash activemq run

# Tui 2 : run bank
bash bank

# Tui 3... : run clients
bask client <client_name>
```

## Extensions design decisions

### Check for account balance
This is using the same concept of a temporary queue on the side of the client as in the initial exchange of account number.
Bank is tracking account balances of each account number.
Balance query and response is a "typed" `TextMessage` with an `int` property of an account name.
This way anybody is able to query account balance of everybody else, so that may be another concern.

### Insufficient funds on the side of the bank
The original synchronous loop looks like this: buyer -> seller -> buyer -> bank -> seller -> buyer.
Everyone is synchronously waiting for a reply and I did not want to bring asynchronous behavior into this protocol because it would require too much change.
Thus I've decided to add a check in the bank where it checks whether the buyer has enough money, if it does not then the Bank still sends a message to the seller that contains this fact and then the seller informs buyer about this.
This way the loop stays unchanged and thus requires little modifications.

The only addition is that the seller sends a special message type with appropriate error to the buyer.

### Not enough money transfered
This is a bit of an issue if we want to keep the original loop, but the idea is that we can add a synchronous step into it that confirms the bank that the amount is sufficient and the seller can sell the product to the buyer.
Only then will the bank make the transaction.
However, this addition to the protocol has still its issues because with unreliable clients a seller can confirm the funds even if they are not sufficient and then not give the goods but that is unavoidable unless we add a means of transfer of goods to the protocol and trust some central authority e.g. Bank that holds onto the goods.

### Update of goods
After a successful transaction the published goods change and I broadcast them this time with asynchronous `eventSender` and `eventSession`.
