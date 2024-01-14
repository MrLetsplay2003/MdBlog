package me.mrletsplay.mdblog.markdown;

public class MdRenderContext {

	private MdRenderer renderer;

	public MdRenderContext(MdRenderer renderer) {
		this.renderer = renderer;
	}

	public MdRenderer getRenderer() {
		return renderer;
	}

}
