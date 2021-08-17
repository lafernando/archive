var image = get("image");
for (var i = 0; i < image.width; i++) {
    for (var j = 0; j < image.height; j++) {
        var pixel = getPixel(image, i, j);
        var value = (pixel.r + pixel.g + pixel.b) / 3;
        setPixel(image, i, j, value, value, value, pixel.a);
    }
}
saveData(image, "image/image");
