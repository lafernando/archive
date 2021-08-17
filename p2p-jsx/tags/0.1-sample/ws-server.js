var http = require("http"), io = require("socket.io");

var server = http.createServer(function(req, res) { 
  res.writeHead(200, { "Content-Type" : "text/html" }); 
  res.end();
});
server.listen(8080);

var socket = io.listen(server);

socket.on("connection", function(client) {

  client.on("message", function(event) { 
    var mode = event.mode;
    if (mode == "REGISTER") {
      registerClient(event.address, this);
    } else if (mode == "RELAY") {
      relay(event.address, event.payload, this);
    } else if (mode == "LIST_PEERS") {
      sendPeerList(this);
    }    
  });

  client.on("disconnect", function() {
    address = session_address_map[client.sessionId];
    delete client_map[address];
    delete session_address_map[client.sessionId];
    console.log("Client with address: " + address + " has disconnected.");
    refreshPeerListForAll();
  });

});

client_map = new Object();
session_address_map = new Object();

function registerClient(address, client) {
  client_map[address] = client;
  session_address_map[client.sessionId] = address;
  console.log("Client registered with address: " + address + ", client: " + client_map[address]);
  refreshPeerListForAll();
}

function refreshPeerListForAll() {
  for (address in client_map) {
    sendPeerList(client_map[address]);
  }
}

function sendPeerList(client) {
  var clientAddress = session_address_map[client.sessionId];
  console.log("Sending peer list to client: " + clientAddress);
  var peerList = [];
  for(var peer in client_map) {
    if (peer != clientAddress) {
      peerList.push(peer);
    }
  }
  var payload = { type:"PEER_LIST", data:peerList };
  var msg = { address:"SERVER", payload:payload };
  client.send(msg);
}

function relay(target_address, data, src_client) {
  src_address = session_address_map[src_client.sessionId];
  if (target_address in client_map) {
    var client = client_map[target_address];
    var client_msg = { address:src_address, payload:data };
    client.send(client_msg);
    console.log("Relaying data, address: " + target_address + " payload: " + client_msg.payload);
  } else {
    console.log("Client with address: " + target_address + " does not exist, message not sent.");
  }
}
