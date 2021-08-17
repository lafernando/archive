var rank = GET_RANK();
var size = GET_SIZE();
if (rank == 0) {
    var global_result = new Object();
    put("global_result", global_result);
    put("root_done", true);
    print("ROOT DONE");
    put("worker_done_count", 0);
    WAIT_FOR_VALUE("worker_done_count", size - 1);
    var result = get("global_result");
    print("OVERALL_RESULT:" + JSON.stringify(result));
    saveData(result);
} else {
    WAIT_FOR_VALUE("root_done", true);
    var len = get("data.length");
    var x = Math.ceil(len / (size - 1));
    var starting_index = x * (rank - 1);
    var end_index = starting_index + x;
    if (rank == size - 1) {
        end_index = len;
    }
    var local_data = get("data.slice(" + starting_index + "," + end_index + ")");
    var result = new Object();
    for (var i = 0; i < local_data.length; i++) {
        if (local_data[i] in result) {
            result[local_data[i]] = result[local_data[i]] + 1;
        } else {
            result[local_data[i]] = 1;
        } 
    }
    print("RESULT:" + JSON.stringify(result));
    BEGIN_MUTEX("worker");
    var global_result = get("global_result");
    for (var key in result) {
        global_result[key] = (global_result[key] == null ? 0 : global_result[key]) + result[key];
    }
    put("global_result", global_result);
    put("worker_done_count", get("worker_done_count") + 1);
    END_MUTEX("worker");
}
