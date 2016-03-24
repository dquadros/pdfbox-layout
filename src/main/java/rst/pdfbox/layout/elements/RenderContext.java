package rst.pdfbox.layout.elements;

import java.io.Closeable;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import rst.pdfbox.layout.Coords;
import rst.pdfbox.layout.WidthRespecting;
import rst.pdfbox.layout.elements.Dividable.Divided;

public class RenderContext implements Closeable {

	private final Document document;
	private final PDDocument pdDocument;
	private PDPage page;
	private int pageIndex = 0;
	private PDPageContentStream contentStream;
	private Coords currentPosition;

	public RenderContext(Document document, PDDocument pdDocument)
			throws IOException {
		this.document = document;
		this.pdDocument = pdDocument;
		newPage();
	}

	public Coords getUpperLeft() {
		return new Coords(document.getMarginLeft(), page.getMediaBox()
				.getHeight() - document.getMarginTop());
	}

	public Coords getCurrentPosition() {
		return currentPosition;
	}

	public void movePositionBy(final float x, final float y) {
		currentPosition = currentPosition.add(x, y);
	}

	public float getWidth() {
		return page.getMediaBox().getWidth() - document.getMarginLeft()
				- document.getMarginRight();
	}

	public float getHeight() {
		return page.getMediaBox().getHeight() - document.getMarginTop()
				- document.getMarginBottom();
	}

	public float getRemainingHeight() {
		return getCurrentPosition().getY() - document.getMarginBottom();
	}

	public Document getDocument() {
		return document;
	}

	public PDDocument getPdDocument() {
		return pdDocument;
	}

	public PDPage getPage() {
		return page;
	}

	public PDPageContentStream getContentStream() {
		return contentStream;
	}

	public void draw(final Drawable drawable) throws IOException {
		if (drawable.getAbsolutePosition() != null) {
			drawAbsolute(drawable, drawable.getAbsolutePosition());
		} else {
			drawReleative(drawable);
		}
	}

	protected void drawReletiveAndMovePosition(final Drawable drawable)
			throws IOException {
		getContentStream().saveGraphicsState();
		getContentStream().addRect(document.getMarginLeft(),
				document.getMarginBottom(), getWidth(), getHeight());
		getContentStream().clip();

		drawable.draw(getContentStream(), getCurrentPosition());

		getContentStream().restoreGraphicsState();

		movePositionBy(0, -drawable.getHeight());
	}

	protected void drawAbsolute(final Drawable drawable, final Coords coords)
			throws IOException {
		drawable.draw(getContentStream(), coords);
	}

	protected void drawReleative(final Drawable drawable) throws IOException {

		float oldMaxWidth = -1;
		if (drawable instanceof WidthRespecting) {
			WidthRespecting flowing = (WidthRespecting) drawable;
			flowing.getMaxWidth();
			if (oldMaxWidth < 0) {
				flowing.setMaxWidth(getWidth());
			}
		}

		Drawable drawablePart = drawable;
		while (getRemainingHeight() < drawablePart.getHeight()) {
			Dividable dividable = null;
			if (drawablePart instanceof Dividable) {
				dividable = (Dividable) drawablePart;
			} else {
				dividable = new Cutter(drawablePart);
			}
			Divided divided = dividable.divide(getRemainingHeight());
			drawReletiveAndMovePosition(divided.getFirst());

			// new page
			newPage();

			drawablePart = divided.getRest();
		}

		if (drawable instanceof WidthRespecting) {
			if (oldMaxWidth < 0) {
				((WidthRespecting) drawable).setMaxWidth(oldMaxWidth);
			}
		}

		drawReletiveAndMovePosition(drawablePart);
	}

	public void newPage() throws IOException {
		if (closePage()) {
			++pageIndex;
		}
		this.page = new PDPage(document.getMediaBox());
		this.pdDocument.addPage(page);
		this.contentStream = new PDPageContentStream(pdDocument, page, true,
				true);
		currentPosition = getUpperLeft();

		document.beforePage(document, pdDocument, pageIndex, page,
				contentStream);
	}

	public boolean closePage() throws IOException {
		if (contentStream != null) {
			document.afterPage(document, pdDocument, pageIndex, page,
					contentStream);
			
			contentStream.close();
			contentStream = null;
			return true;
		}
		return false;
	}

	@Override
	public void close() throws IOException {
		closePage();
	}
}
