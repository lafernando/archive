/** Socket.IO 0.6.2 - Built with build.js */
/**
 * Socket.IO client
 * 
 * @author Guillermo Rauch <guillermo@learnboost.com>
 * @license The MIT license.
 * @copyright Copyright (c) 2010 LearnBoost <dev@learnboost.com>
 */

var io = this.io = {
	version: '0.6.2',
	
	setPath: function(path){
		this.path = "";
	}
};

/**
 * Socket.IO client
 * 
 * @author Guillermo Rauch <guillermo@learnboost.com>
 * @license The MIT license.
 * @copyright Copyright (c) 2010 LearnBoost <dev@learnboost.com>
 */

(function(){
	var io = this.io;

	var _pageLoaded = false;

	io.util = {

		ios: false,

		load: function(fn){

		},

		inherit: function(ctor, superCtor){
			// no support for `instanceof` for now
			for (var i in superCtor.prototype){
				ctor.prototype[i] = superCtor.prototype[i];
			}
		},

		indexOf: function(arr, item, from){
			for (var l = arr.length, i = (from < 0) ? Math.max(0, l + from) : from || 0; i < l; i++){
				if (arr[i] === item) return i;
			}
			return -1;
		},

		isArray: function(obj){
			return Object.prototype.toString.call(obj) === '[object Array]';
		},
		
    merge: function(target, additional){
      for (var i in additional)
        if (additional.hasOwnProperty(i))
          target[i] = additional[i];
    }

	};

	io.util.load(function(){
		_pageLoaded = true;
	});

})();

/**
 * Socket.IO client
 * 
 * @author Guillermo Rauch <guillermo@learnboost.com>
 * @license The MIT license.
 * @copyright Copyright (c) 2010 LearnBoost <dev@learnboost.com>
 */

// abstract

(function(){
	var io = this.io;
	
	var frame = '~m~',
	
	stringify = function(message){
		if (Object.prototype.toString.call(message) == '[object Object]'){
			return '~j~' + JSON.stringify(message);
		} else {
			return String(message);
		}
	};
	
	Transport = io.Transport = function(base, options){
		this.base = base;
		this.options = {
			timeout: 15000 // based on heartbeat interval default
		};
		io.util.merge(this.options, options);
	};

	Transport.prototype.send = function(){
		throw new Error('Missing send() implementation');
	};

	Transport.prototype.connect = function(){
		throw new Error('Missing connect() implementation');
	};

	Transport.prototype.disconnect = function(){
		throw new Error('Missing disconnect() implementation');
	};
	
	Transport.prototype._encode = function(messages){
		var ret = '', message,
				messages = io.util.isArray(messages) ? messages : [messages];
		for (var i = 0, l = messages.length; i < l; i++){
			message = messages[i] === null || messages[i] === undefined ? '' : stringify(messages[i]);
			ret += frame + message.length + frame + message;
		}
		return ret;
	};
	
	Transport.prototype._decode = function(data){
		var messages = [], number, n;
		do {
			if (data.substr(0, 3) !== frame) return messages;
			data = data.substr(3);
			number = '', n = '';
			for (var i = 0, l = data.length; i < l; i++){
				n = Number(data.substr(i, 1));
				if (data.substr(i, 1) == n){
					number += n;
				} else {	
					data = data.substr(number.length + frame.length);
					number = Number(number);
					break;
				} 
			}
			messages.push(data.substr(0, number)); // here
			data = data.substr(number);
		} while(data !== '');
		return messages;
	};
	
	Transport.prototype._onData = function(data){
		this._setTimeout();
		var msgs = this._decode(data);
		if (msgs && msgs.length){
			for (var i = 0, l = msgs.length; i < l; i++){
				this._onMessage(msgs[i]);
			}
		}
	};
	
	Transport.prototype._setTimeout = function(){
		var self = this;
		if (this._timeout) clearTimeout(this._timeout);
		this._timeout = setTimeout(function(){
			self._onTimeout();
		}, this.options.timeout);
	};
	
	Transport.prototype._onTimeout = function(){
		this._onDisconnect();
	};
	
	Transport.prototype._onMessage = function(message){
		if (!this.sessionid){
			this.sessionid = message;
			this._onConnect();
		} else if (message.substr(0, 3) == '~h~'){
			this._onHeartbeat(message.substr(3));
		} else if (message.substr(0, 3) == '~j~'){
			this.base._onMessage(JSON.parse(message.substr(3)));
		} else {
			this.base._onMessage(message);
		}
	},
	
	Transport.prototype._onHeartbeat = function(heartbeat){
		this.send('~h~' + heartbeat); // echo
	};
	
	Transport.prototype._onConnect = function(){
		this.connected = true;
		this.connecting = false;
		this.base._onConnect();
		this._setTimeout();
	};

	Transport.prototype._onDisconnect = function(){
		this.connecting = false;
		this.connected = false;
		this.sessionid = null;
		this.base._onDisconnect();
	};

	Transport.prototype._prepareUrl = function(){
		return (this.base.options.secure ? 'https' : 'http') 
			+ '://' + this.base.host 
			+ ':' + this.base.options.port
			+ '/' + this.base.options.resource
			+ '/' + this.type
			+ (this.sessionid ? ('/' + this.sessionid) : '/');
	};

})();

/**
 * Socket.IO client
 * 
 * @author Guillermo Rauch <guillermo@learnboost.com>
 * @license The MIT license.
 * @copyright Copyright (c) 2010 LearnBoost <dev@learnboost.com>
 */

(function(){
	var io = this.io;
	
	var WS = io.Transport.websocket = function(){
		io.Transport.apply(this, arguments);
	};
	
	io.util.inherit(WS, io.Transport);
	
	WS.prototype.type = 'websocket';
	
	WS.prototype.connect = function(){
		var self = this;
		this.socket = new WebSocket(this._prepareUrl());
		this.socket.onmessage = function(ev){ self._onData(ev.data); };
		this.socket.onclose = function(ev){ self._onClose(); };
    this.socket.onerror = function(e){ self._onError(e); };
		return this;
	};
	
	WS.prototype.send = function(data){
		if (this.socket) this.socket.send(this._encode(data));
		return this;
	};
	
	WS.prototype.disconnect = function(){
		if (this.socket) this.socket.close();
		return this;
	};
	
	WS.prototype._onClose = function(){
		this._onDisconnect();
		return this;
	};

  WS.prototype._onError = function(e){
    this.base.emit('error', [e]);
  };
	
	WS.prototype._prepareUrl = function(){
		return (this.base.options.secure ? 'wss' : 'ws') 
		+ '://' + this.base.host 
		+ ':' + this.base.options.port
		+ '/' + this.base.options.resource
		+ '/' + this.type
		+ (this.sessionid ? ('/' + this.sessionid) : '');
	};
	
	WS.check = function(){
		// we make sure WebSocket is not confounded with a previously loaded flash WebSocket
		return typeof WebSocket !== "undefined";
	};

	WS.xdomainCheck = function(){
		return true;
	};
	
})();


/**
 * Socket.IO client
 * 
 * @author Guillermo Rauch <guillermo@learnboost.com>
 * @license The MIT license.
 * @copyright Copyright (c) 2010 LearnBoost <dev@learnboost.com>
 */

(function(){
	var io = this.io;
	
	var Socket = io.Socket = function(host, options){
		this.host = host;
		this.options = {
			secure: false,
			document: null,
			port: 80,
			resource: 'socket.io',
			transports: ['websocket', 'flashsocket', 'htmlfile', 'xhr-multipart', 'xhr-polling', 'jsonp-polling'],
			transportOptions: {
				'xhr-polling': {
					timeout: 25000 // based on polling duration default
				},
				'jsonp-polling': {
					timeout: 25000
				}
			},
			connectTimeout: 5000,
			reconnect: true,
			reconnectionDelay: 500,
			maxReconnectionAttempts: 10,
			tryTransportsOnConnectTimeout: true,
			rememberTransport: true
		};
		io.util.merge(this.options, options);
		this.connected = false;
		this.connecting = false;
		this._events = {};
		this.transport = this.getTransport();
	};
	
	Socket.prototype.getTransport = function(override){
		var transports = override || this.options.transports, match;
		if (this.options.rememberTransport && !override){
			//match = this.options.document.cookie.match('(?:^|;)\\s*socketio=([^;]*)');
                        match = false;
			if (match){
				this._rememberedTransport = true;
				transports = [decodeURIComponent(match[1])];
			}
		} 
		for (var i = 0, transport; transport = transports[i]; i++){
			if (io.Transport[transport] 
				&& io.Transport[transport].check() 
				&& (!this._isXDomain() || io.Transport[transport].xdomainCheck())){
				return new io.Transport[transport](this, this.options.transportOptions[transport] || {});
			}
		}
		return null;
	};
	
	Socket.prototype.connect = function(){
		if (this.transport && !this.connected){
			if (this.connecting) this.disconnect(true);
			this.connecting = true;
			this.emit('connecting', [this.transport.type]);
			this.transport.connect();
			if (this.options.connectTimeout){
				var self = this;
				this.connectTimeoutTimer = setTimeout(function(){
					if (!self.connected){
						self.disconnect(true);
						if (self.options.tryTransportsOnConnectTimeout && !self._rememberedTransport){
							if(!self._remainingTransports) self._remainingTransports = self.options.transports.slice(0);
							var transports = self._remainingTransports;
							while(transports.length > 0 && transports.splice(0,1)[0] != self.transport.type){}
							if(transports.length){
								self.transport = self.getTransport(transports);
								self.connect();
							}
						}
						if(!self._remainingTransports || self._remainingTransports.length == 0) self.emit('connect_failed');
					}
					if(self._remainingTransports && self._remainingTransports.length == 0) delete self._remainingTransports;
				}, this.options.connectTimeout);
			}
		}
		return this;
	};
	
	Socket.prototype.send = function(data){
		if (!this.transport || !this.transport.connected) return this._queue(data);
		this.transport.send(data);
		return this;
	};
	
	Socket.prototype.disconnect = function(reconnect){
    if (this.connectTimeoutTimer) clearTimeout(this.connectTimeoutTimer);
		if (!reconnect) this.options.reconnect = false;
		this.transport.disconnect();
		return this;
	};
	
	Socket.prototype.on = function(name, fn){
		if (!(name in this._events)) this._events[name] = [];
		this._events[name].push(fn);
		return this;
	};
	
  Socket.prototype.emit = function(name, args){
    if (name in this._events){
      var events = this._events[name].concat();
      for (var i = 0, ii = events.length; i < ii; i++)
        events[i].apply(this, args === undefined ? [] : args);
    }
    return this;
  };

	Socket.prototype.removeEvent = function(name, fn){
		if (name in this._events){
			for (var a = 0, l = this._events[name].length; a < l; a++)
				if (this._events[name][a] == fn) this._events[name].splice(a, 1);		
		}
		return this;
	};
	
	Socket.prototype._queue = function(message){
		if (!('_queueStack' in this)) this._queueStack = [];
		this._queueStack.push(message);
		return this;
	};
	
	Socket.prototype._doQueue = function(){
		if (!('_queueStack' in this) || !this._queueStack.length) return this;
		this.transport.send(this._queueStack);
		this._queueStack = [];
		return this;
	};
	
	Socket.prototype._isXDomain = function(){
            return true;
	};
	
	Socket.prototype._onConnect = function(){
		this.connected = true;
		this.connecting = false;
		this._doQueue();
		//if (this.options.rememberTransport) this.options.document.cookie = 'socketio=' + encodeURIComponent(this.transport.type);
		this.emit('connect');
	};
	
	Socket.prototype._onMessage = function(data){
		this.emit('message', [data]);
	};
	
	Socket.prototype._onDisconnect = function(){
		var wasConnected = this.connected;
		this.connected = false;
		this.connecting = false;
		this._queueStack = [];
		if (wasConnected){
			this.emit('disconnect');
			if (this.options.reconnect && !this.reconnecting) this._onReconnect();
		}
	};
	
	Socket.prototype._onReconnect = function(){
		this.reconnecting = true;
		this.reconnectionAttempts = 0;
		this.reconnectionDelay = this.options.reconnectionDelay;
		
		var self = this
			, tryTransportsOnConnectTimeout = this.options.tryTransportsOnConnectTimeout
			, rememberTransport = this.options.rememberTransport;
		
		function reset(){
			if(self.connected) self.emit('reconnect',[self.transport.type,self.reconnectionAttempts]);
			self.removeEvent('connect_failed', maybeReconnect).removeEvent('connect', maybeReconnect);
			delete self.reconnecting;
			delete self.reconnectionAttempts;
			delete self.reconnectionDelay;
			delete self.reconnectionTimer;
			delete self.redoTransports;
			self.options.tryTransportsOnConnectTimeout = tryTransportsOnConnectTimeout;
			self.options.rememberTransport = rememberTransport;
			
			return;
		};
		
		function maybeReconnect(){
			if (!self.reconnecting) return;
			if (!self.connected){
				if (self.connecting && self.reconnecting) return self.reconnectionTimer = setTimeout(maybeReconnect, 1000);
				
				if (self.reconnectionAttempts++ >= self.options.maxReconnectionAttempts){
					if (!self.redoTransports){
						self.on('connect_failed', maybeReconnect);
						self.options.tryTransportsOnConnectTimeout = true;
						self.transport = self.getTransport(self.options.transports); // overwrite with all enabled transports
						self.redoTransports = true;
						self.connect();
					} else {
						self.emit('reconnect_failed');
						reset();
					}
				} else {
					self.reconnectionDelay *= 2; // exponential backoff
					self.connect();
					self.emit('reconnecting', [self.reconnectionDelay,self.reconnectionAttempts]);
					self.reconnectionTimer = setTimeout(maybeReconnect, self.reconnectionDelay);
				}
			} else {
				reset();
			}
		};
		this.options.tryTransportsOnConnectTimeout = false;
		this.reconnectionTimer = setTimeout(maybeReconnect, this.reconnectionDelay);
		
		this.on('connect', maybeReconnect);
	};

  Socket.prototype.fire = Socket.prototype.emit;
	Socket.prototype.addListener = Socket.prototype.addEvent = Socket.prototype.addEventListener = Socket.prototype.on;
	Socket.prototype.removeListener = Socket.prototype.removeEventListener = Socket.prototype.removeEvent;
	
})();

(function() {
  
  if (typeof WebSocket !== "undefined") return;
  
})();

///////////////////////////////////////////////// comm.js


var comm_socket = null;

function init_comm(myAddress, receiveMessageCallback) {
  comm_socket = new io.Socket('173.230.138.125', {port: 8080});
  comm_socket.connect(); 
  comm_socket.on('connect', function() {
    postMessage('Client with address ' + myAddress + " connected to server.");
  });
  comm_socket.on('message', function(msg) {
    receiveMessageCallback(msg.address, msg.payload);
  });
  comm_socket.on('disconnect', function() {
    postMessage('Client disconnected.');
  });
  var registerMessage = { mode:"REGISTER", address:myAddress};
  comm_socket.send(registerMessage);
  postMessage("DONE");
}

function sendMessage(destAddress, payload) {
  var out_msg = { mode:"RELAY", address:destAddress, payload:payload };
  comm_socket.send(out_msg);
}

////////////////////////////////////////////////// utils.js

function randomString() {
  result = "";
  var n = "";
  for (var i = 0; i < 32; i++) {
    n = Math.floor(Math.random() * 16).toString(16).toUpperCase();
    result += n;
  }
  return result;
}

function generateAddress() {
  return randomString();
}

function receiveMessage(address, payload) {
  postMessage("GGG:" + payload);
}

onmessage = function (event) {
  var address = event.data;
  init_comm(address, receiveMessage);
  run();
};

function run() {
  postMessage("ABC");
}


