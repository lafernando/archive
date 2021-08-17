var image = get("image");
for (var i = 0; i < image.width; i++) {
    for (var j = 0; j < image.height; j++) {
        var pixel = getPixel(image, i, j);
        setPixel(image, i, j, pixel.r * 2, pixel.g / 2, pixel.b / 2, pixel.a);
    }
}
saveData(image, "image/image");
