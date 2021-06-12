from flask import Flask
import datetime

import time
import threading
import os

arranque=0
vida=0

if 'ARRANQUE' in os.environ:
	arranque = int(os.environ['ARRANQUE'])
	print("ARRANQUE: {}".format(arranque))

if 'VIDA' in os.environ:
	vida = int(os.environ['VIDA'])
	print("VIDA: {}".format(vida))

if arranque>0:
	time.sleep(arranque)

app = Flask(__name__)

global status
status = 200

@app.route("/")
def main():
	currentDT = datetime.datetime.now()
	
	if (status == 200):
		time.sleep(0.5)

	return "[{}]Welcome user! current time is {} ".format(os.environ['VERSION'], str(currentDT)),status

@app.route("/health")
def health():
	return "OK"


@app.route("/addfault")
def addfault():
    global status
    if (status == 200):
        status = 503
    else:
        status = 200
    return "OK"

def exit_after():
	time.sleep(vida)
	os._exit(1)

if vida > 0:
	exit_thread = threading.Thread(target=exit_after)
	exit_thread.start()

if __name__ == "__main__":
	app.run(host='0.0.0.0')
