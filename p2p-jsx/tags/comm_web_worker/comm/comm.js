
var comm_socket = null;

function init_comm(myAddress, receiveMessageCallback) {
  comm_socket = new io.Socket('173.230.138.125', {port: 8080});
  comm_socket.connect(); 
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
  comm_socket.send(registerMessage);
}

function sendMessage(destAddress, payload) {
  var out_msg = { mode:"RELAY", address:destAddress, payload:payload };
  comm_socket.send(out_msg);
}

