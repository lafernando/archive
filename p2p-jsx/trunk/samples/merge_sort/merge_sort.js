var rank = GET_RANK();
var size = GET_SIZE();
if (rank == 0) {
    var worker_results = new Array();
    put("worker_results", worker_results);
    put("root_done", true);
    print("ROOT DONE");
    put("worker_done_count", 0);
    WAIT_FOR_VALUE("worker_done_count", size - 1);
    var worker_results = get("worker_results");
    var result = worker_results[0];
    for (var i = 1; i < worker_results.length; i++) {
        result = merge(result, worker_results[i]);
    }
    saveData(JSON.stringify(result));
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
    local_data.sort();
    print("RESULT SIZE:" + local_data.length);
    put("worker_results[" + (rank - 1) + "]", local_data);
    BEGIN_MUTEX("worker");
    put("worker_done_count", get("worker_done_count") + 1);
    END_MUTEX("worker");
}

function merge(left, right) {
    var result = new Array();
    while((left.length > 0) && (right.length > 0)) {
        if(left[0].localeCompare(right[0]) <= 0) {
            result.push(left.shift());
        } else {
            result.push(right.shift());
        }
    }
    while(left.length > 0) {
        result.push(left.shift());
    }
    while(right.length > 0) {
        result.push(right.shift());
    }
    return result;
}
