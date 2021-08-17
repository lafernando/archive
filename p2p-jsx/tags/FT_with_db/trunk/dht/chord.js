
function ChordDHT(myAddress) {
    this.myAddress = myAddress;
    this.dhtAddress = Sha1.hash(this.myAddress);
    this.fingerTable = [];
    this.ringSize = 128;
    /* to keep the values for request calls, the key is correlation id, and the value is the value of the variable */
    this.variableCache = [];

    for (var i = 0; i < this.ringSize; i++) {
        this.fingerTable[i] = this.myAddress;
    }

    console.log("My Chord Address:" + this.dhtAddress);

    /**
     * Strucuture of the message:-
     * msg.type = "REQUEST" | "REPLY"
     * msg.data = <payload>
     */
    this.receiveMessage = function(msg) {
        console.log("AAA:" + msg.type);
        console.log("BBB:" + msg.data);
        if (msg.type == "REQUEST") {
            var request = msg.data;
            this.receivedRequest(request.correlationId, request.replyToAddress, request.toAddress, request.msg);
        } else if (msg.type == "REPLY") {
            var reply = msg.data;
            this.receivedReply(reply.correlationId, reply.data);
        } else {
            console.log("Unknown message: " + msg);
        }
    };

    this.requestAsync = function(correlationId, replyToAddress, toAddress, msg) {
        var out = new Object();
        out.type = "REQUEST";
        out.data = new Object();
        out.data.correlationId = randomString();
        out.data.replyToAddress = replyToAddress;
        out.data.msg = msg;
        sendMessage(toAddress, out);
    };

    this.request = function (toAddress, msg) {
        var correlationId = randomString();
        this.requestAsync(correlationId, this.myAddress, toAddress, msg);
        var value = undefined;
        alert("XXX");
        value = this.variableCache[correlationId];
        //delete this.variableCache[correlationId];
        return value;
    };

    this.receivedRequest = function(correlationId, replyToAddress, toAddress, msg) {
        var msg = new Object();
        msg.type = "REPLY";
        msg.data = new Object();
        msg.data.correlationId = correlationId;
        msg.data.data = "RESULT";
        sendMessage(replyToAddress, msg);
    };

    this.receivedReply = function(correlationId, data) {
        this.variableCache[correlationId] = data;
        console.log("REPLY:" + data);
    };

}

