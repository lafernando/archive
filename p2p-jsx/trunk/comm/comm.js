
var comm_socket = null;

function init_comm(myAddress, receiveMessageCallback) {
  comm_socket = io.connect('http://p2pjs.lafernando.org', {port: 8080});
  comm_socket.on('connect', function() {
    console.log('Client with address ' + myAddress + " connected to server.");
  });
  comm_socket.on('message', function(msg) {
    receiveMessageCallback(msg.address, msg.payload);
  });
  comm_socket.on('disconnect', function() {
    console.log('Client disconnected.');
  });
  var registerMessage = { mode:"REGISTER", address:myAddress};
  comm_socket.json.send(registerMessage);
}

function sendMessage(destAddress, payload) {
  var out_msg = { mode:"RELAY", address:destAddress, payload:payload };
  comm_socket.json.send(out_msg);
}

