
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

