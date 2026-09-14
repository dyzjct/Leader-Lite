package leader.util.shader;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL20;

public class OutlineShader extends Shader {
    private static final String shader = String.join(
            "\n",
            "#version 120",
            "uniform sampler2D texture;",
            "uniform vec2 size;",
            "void main(void) {",
            "vec2 uv = gl_TexCoord[0].st;",
            "vec4 center = texture2D(texture, uv);",
            "vec4 outline = vec4(0.0);",
            "for (int x = -2; x <= 2; x++) {",
            "for (int y = -2; y <= 2; y++) {",
            "vec4 sample = texture2D(texture, uv + vec2(float(x), float(y)) * size);",
            "if (sample.a > outline.a) {",
            "outline = sample;",
            "}",
            "}",
            "}",
            "gl_FragColor = center.a > 0.0 ? vec4(0.0) : outline;",
            "}"
    );

    public OutlineShader() {
        super(shader);
    }

    @Override
    public void onLink() {
        this.setUniform("texture");
        this.setUniform("size");
    }

    @Override
    public void onUse() {
        GL20.glUseProgram(this.programId);
        int texLoc = this.getUniformLocationCached("texture");
        GL20.glUniform1i(texLoc, 0);
        int sizeLoc = this.getUniformLocationCached("size");
        float invW = 1.0f / Minecraft.getMinecraft().displayWidth;
        float invH = 1.0f / Minecraft.getMinecraft().displayHeight;
        GL20.glUniform2f(sizeLoc, invW, invH);
    }
}
