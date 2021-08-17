var MAX_VALUE = 1000000;

function longToBytes(key) {
    var result = new Array();
    for (var i = 0; i < 8; i++) {
        result[i] = key & 255;
        key = key >>> 8;
    }
    return result;
}

function bytesToString(value) {
    var result = "";
    for (var i = 0; i < value.length; i++) {
        result += String.fromCharCode(value[i]);
    }
    return result;
}

function xor(data, key) {
    var result = new Array();
    var keyBytes = longToBytes(key);
    for (var i = 0; i < data.length; i += 8) {
    	for (var j = 0; j < 8; j++) {
	    if (i + j >= data.length) {
		break;
	    }
	    result[i + j] = (data.charCodeAt(i + j) ^ keyBytes[j]);
	}
    }
    return result;
}

function checkValid(val, hint) {
    for (var i = 0; i < val.length; i++) {
        if (!isAsciiPrintable(val.charCodeAt(i))) {
	    return false;
	}
    }
    return (val.indexOf(hint) != -1);
}
	
function isAsciiPrintable(ch) {
    return ch >= 32 && ch < 127;
}

function enc(data, key) {
    return bytesToString(xor(data, key));
}

function dec(data, key) {
    return bytesToString(xor(data, key));
}

var rank = GET_RANK();
var size = GET_SIZE();

function crack(data, hint, startKey) {
    print("CRACKING START - " + startKey + " RANK - " + rank);
    var start_time = new Date().getTime();
    var currentKey = startKey;
    var i = 0;
    while (true) {
        i++;
        if (i == MAX_VALUE) break;
        if (i % 500000 == 0) {
            print("CHECKING STATUS:" + i);
            if (get("work_done") != null) { 
                print("Another worker found the solution, exiting..");
                break;
            }
        }
        currentKey++;
        var tmpData = dec(data, currentKey);
        if (checkValid(tmpData, hint)) {
            var end_time = new Date().getTime();
            result  = "HIT: " + currentKey + ": " + tmpData + " Time: " + ((end_time - start_time) / 1000) + " seconds.";
            put("work_done", 1);
            put("result", result);
            break;
        }        
    }
}

if (rank == 0) {
    var data = "Alexander III of Macedon (20/21 July 356 - 10/11 June 323 BC), commonly known as Alexander the Great, was a king of Macedon, a state in northern ancient Greece";
    var key = Math.ceil(Math.random() * MAX_VALUE);
    var hint = "Alexander";
    var ex = enc(data, key);
    put("data", ex);
    put("hint", hint);
    print("Enc-Key: " + key);
    put("root_done", true);
    print("ROOT DONE");
    WAIT_FOR_VALUE("work_done", 1);
    var result = get("result");
    print("RESULT: " + result);
} else {
    WAIT_FOR_VALUE("root_done", true);
    var ex = get("data");
    var hint = get("hint");
    crack(ex, hint, MAX_VALUE / (size - 1) * (rank - 1));
}

