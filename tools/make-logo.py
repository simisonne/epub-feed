"""Render the ePUB Feed launcher icon to docs/logo.png for the README."""
from PIL import Image, ImageDraw

S = 8                      # supersample factor
CANVAS = 108 * S           # icon canvas in dp, scaled
BG = (10, 10, 10, 255)     # ic_launcher_background
BLUE = (29, 155, 240, 255)  # 1D9BF0
GREY = (231, 233, 234, 255)  # E7E9EA

img = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# Rounded square like the launcher mask (adaptive icons mask to ~22% radius).
d.rounded_rectangle([0, 0, CANVAS - 1, CANVAS - 1], radius=24 * S, fill=BG)


def bar(x0, y0, x1, y1, r, colour, dy=4.5):
    """One post card from ic_launcher_foreground.xml, with the group's translateY."""
    d.rounded_rectangle(
        [x0 * S, (y0 + dy) * S, x1 * S, (y1 + dy) * S],
        radius=r * S,
        fill=colour,
    )


bar(30, 30, 78, 42, 4, BLUE)
bar(31, 46, 77, 55, 3, GREY)
bar(31, 60, 63, 69, 3, GREY)

img.resize((512, 512), Image.LANCZOS).save("docs/logo.png")
print("docs/logo.png", Image.open("docs/logo.png").size)
