import datetime
import time

from google.cloud import pubsub

def process_message(message):
    """Process received message"""
    print("[{0}] Processing: {1}".format(datetime.datetime.now(),
                                         message.message_id))
    time.sleep(3)
    print("[{0}] Processed: {1}".format(datetime.datetime.now(),
                                        message.message_id))

if __name__ == '__main__':
    
    """Creating a pubsub subscriber"""
    client = pubsub.Client()
    subscription = client.topic("scaling-gcp-topic").subscription("scaling-gcp-topic-read")

    print('Pulling message from pubsub contineously in infinte loop.')
    while True:
        with pubsub.subscription.AutoAck(subscription, max_messages=10) as ack:
            for _, message in list(ack.items()):
                print("[{0}] Got Message: ID={1} Data={2}".format(
                    datetime.datetime.now(),
                    message.message_id,
                    message.data))
                process_message(message)
