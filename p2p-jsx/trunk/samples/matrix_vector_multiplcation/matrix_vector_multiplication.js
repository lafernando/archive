var rank = GET_RANK();
if (rank == 0) {
    print("PARENT SET");
    var matrix = new Array();
    matrix[0] = [1,2,3,4,5,3,6,7,3,6,7,2,6,4,7,2];
    matrix[1] = [5,3,5,1,2,5,6,3,2,2,7,3,7,8,3,7];
    matrix[2] = [3,4,1,5,6,3,6,7,9,4,6,7,3,5,8,3];
    put("m1", matrix);
    var vector = [2,3,4,5,6,7,8,9,2,5,6,2,6,5,2,3];
    put("v1", vector);
    var status = new Array();
    put("status", status);
    put("parent_init", true);
    print("PARENT INIT DONE");
    WAIT_FOR_VALUE("status.length", 3);
    status = get("status");
    print("DONE MASTER:" + status);        
} else {
    WAIT_FOR_VALUE("parent_init", true);
    print("SLAVE STARTING..");
    var row = get("m1["+rank+"]");
    var v1 = get("v1");
    var x = 0;
    for (var i = 0; i < v1.length; i++) {
         x += row[i] * v1[i];
    }
    put("status["+rank+"]", x);
    print("DONE SLAVE:" + len + ":" + x);
}    
