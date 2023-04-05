package legacyfix.agent;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.ClassFile;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;
import legacyfix.util.AssetIndexUtils;
import legacyfix.util.AssetIndexUtils.OSEnum;

public class LegacyFixAgent {

	public static boolean patchMouse = false;
	public static boolean disableControllers = false;
	public static boolean disableGamma = false;
	public static boolean fix15aMP = false;
	public static boolean fixJ6Refs = false;
	public static boolean preclassicJ5 = false;
	public static boolean fixAMD = false;
	public static boolean deAWT = false;

	public static String frameName = "Minecraft";
	public static String iconPath = null;

	public static String levelFile = null;

	static ByteBuffer pixels16 = null;
	static ByteBuffer pixels32 = null;

	static boolean mousedxymatched = false;
	static String canvasClassName = null;
	static String mcappletname = null;
	static CtClass mcclas = null;
	static CtClass mouseHelper = null;

	private static ByteBuffer getIconForLWJGL(InputStream stream, int resolution) {
		try {
			final Image read = ImageIO.read(stream).getScaledInstance(resolution, resolution, Image.SCALE_SMOOTH);
			BufferedImage bufImg = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_ARGB);
			Graphics g = bufImg.getGraphics();
			g.drawImage(read, 0, 0, null);
			g.dispose();

			final int[] rgb = bufImg.getRGB(0, 0, resolution, resolution, null, 0, resolution);
			final ByteBuffer allocate = ByteBuffer.allocate(4 * rgb.length);
			for (final int n : rgb) {
				allocate.putInt(n << 8 | (n >> 24 & 0xFF));
			}
			allocate.flip();
			return allocate;
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}

	public static void premain(String args, final Instrumentation inst) {
		try {
			patchMouse		= System.getProperty("lf.patchMouse") != null;
			disableControllers	= System.getProperty("lf.disableControllers") != null;
			disableGamma		= System.getProperty("lf.disableGamma") != null;
			fix15aMP		= System.getProperty("lf.fix15aMP") != null;
			fixJ6Refs		= System.getProperty("lf.fixJava6References") != null;
			preclassicJ5		= System.getProperty("lf.preclassicJava5") != null;
			fixAMD			= "true".equalsIgnoreCase(System.getProperty("lf.fixAMD"));
			deAWT			= System.getProperty("lf.deAWT") != null;
			iconPath		= System.getProperty("lf.icon");
			frameName		= System.getProperty("lf.frameName");

			levelFile		= System.getProperty("lf.classicLevelPath");

			System.out.println("patchmacmouse - " + patchMouse + "\nfix15a - " + fix15aMP + "\ndeAWT - " + deAWT);

			if (iconPath != null) {
				File iconFile = new File(iconPath);
				if (iconFile.exists() && iconFile.isFile()) {
					pixels32 = getIconForLWJGL(new FileInputStream(iconFile), 32);
					pixels16 = getIconForLWJGL(new FileInputStream(iconFile), 16);
				} else {
					pixels16 = getIconForLWJGL(LegacyFixAgent.class.getResourceAsStream("/favicon.png"), 16);
					pixels32 = getIconForLWJGL(LegacyFixAgent.class.getResourceAsStream("/favicon.png"), 32);
				}
			} else {
				pixels16 = getIconForLWJGL(LegacyFixAgent.class.getResourceAsStream("/favicon.png"), 16);
				pixels32 = getIconForLWJGL(LegacyFixAgent.class.getResourceAsStream("/favicon.png"), 32);
			}

			// ------------------------------------------------

			/*
			 * Start redefining classes
			 */

			// ------------------------------------------------

			final ClassPool pool = ClassPool.getDefault();
			String name;
			CtClass clas;
			CtMethod meth;

			CtClass string = pool.get("java.lang.String");
			CtClass intclas = pool.get("int");
			CtClass floatclas = pool.get("float");

			/*
			 * Strips Minecraft off any references to AWT/swing.
			 * Improves performance. Is required for MacOS M1 fix.
			 */
			if (deAWT) {

				// Note: Forge support for deAWT is BROKEN!
				// Let it die, or redistribute patched versions of Forge.

				byte[] appletbytes = deAWTApplet(pool);
				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(mcappletname), appletbytes)});

				if (mcclas == null) {
					System.out.println("Failed to get minecraft class!!!");
				} else {
					deAWTPatch(pool, pool.getClassLoader().getClass().getProtectionDomain());
					inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(mcclas.getName()), deAWTMain(pool))});
					inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(canvasClassName), deAWTCanvas(pool))});

					// Hooks for LWJGL to set title, icons, resizable status
					// and a part of M1 Macs color patch
					name = "org.lwjgl.opengl.Display";
					clas = pool.get(name);
					meth = clas.getDeclaredMethod("setTitle", new CtClass[] {string});

					// on init
					meth.insertBefore(
							"$1 = \"" + frameName + "\";" +
							"org.lwjgl.opengl.Display.setResizable(true);" +
							"java.lang.reflect.Field f16 = java.lang.ClassLoader.getSystemClassLoader()" +
							"		.loadClass(\"legacyfix.agent.LegacyFixAgent\").getDeclaredField(\"pixels16\");" +
							"f16.setAccessible(true);" +
							"java.nio.ByteBuffer pix16 = f16.get(null);" +
							"java.lang.reflect.Field f32 = java.lang.ClassLoader.getSystemClassLoader()" +
							"		.loadClass(\"legacyfix.agent.LegacyFixAgent\").getDeclaredField(\"pixels32\");" +
							"f32.setAccessible(true);" +
							"java.nio.ByteBuffer pix32 = f32.get(null);" +
							"org.lwjgl.opengl.Display.setIcon(new java.nio.ByteBuffer[] {pix16, pix32});"
					);

					// on tick - couldn't really hook anywhere else, this looks like a safe spot
					meth = clas.getDeclaredMethod("isCloseRequested");

					meth.insertBefore(
							"if (org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER).contains(\"Apple M\")) {" + 
							"	org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB);" +
							"}"
					);

					inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});
				}
			}

			// TODO 
			// -redirect references to .minecraft/assets to mapped hashpaths
			//  (affects 1.6 to 1.7.2 and their snapshots)
			// -redirect references to .minecraft/resources to mapped hashpaths
			// -redirect references to classic level save
			if (true) {
				name = "java.io.File";
				clas = pool.get(name);

				CtConstructor[] constr = new CtConstructor[3];
				constr[0] = clas.getDeclaredConstructor(new CtClass[] {string});
				constr[1] = clas.getDeclaredConstructor(new CtClass[] {string, string});
				constr[2] = clas.getDeclaredConstructor(new CtClass[] {clas, string});

				CtClass filefilter = pool.get("java.io.FileFilter");

				meth = clas.getDeclaredMethod("isDirectory");
				meth.insertBefore(
						"if ($0.path.endsWith(\"assets\") || $0.path.endsWith(\"assets/\")) {" +
						"	return true;" +
						"}"
				);

//				meth = clas.getDeclaredMethod("toURI");
//				meth.insertBefore(
//						"System.out.println($0.getAbsolutePath());"
//				);

				CtMethod[] listFiles = new CtMethod[2];
				listFiles[0] = clas.getDeclaredMethod("listFiles");
				listFiles[1] = clas.getDeclaredMethod("listFiles", new CtClass[] {filefilter});
				// TODO: This has to serve leveled paths instead of delivering everything at once.
				// That is true for every version pre-1.7.3.
				for (CtMethod lfMeth : listFiles) {
					lfMeth.insertBefore(
							"if ($0.path.endsWith(\"assets\") || $0.path.endsWith(\"assets/\") || $0.path.endsWith(\"resources\") || $0.path.endsWith(\"resources/\")) {" +
							"	System.out.println(\"DEBUG ASSETS REF DETECTED \\n \\n\\n\\n\");" +
							"	Thread.sleep(1000L);" +
							"	java.util.ArrayList list = (java.util.ArrayList) java.lang.ClassLoader.getSystemClassLoader()" +
							"		.loadClass(\"legacyfix.util.AssetIndexUtils\").getMethod(\"asFileArray\", null).invoke(null, null);" +
							"	return list.toArray(new java.io.File[list.size()]);" +
							"}"
					);
				}

				for (CtConstructor c : constr) {
					c.insertAfter(
							"if (" + (levelFile != null) + " && $1.equals(\"level.dat\")) {" +
							"	$0.path = java.io.DefaultFileSystem.getFileSystem().normalize(\"" + levelFile + "\");" +
							"	return;" +
							"}" +
//							"System.out.println(\"File init: '\" + $1 + \"'\");" +
//							"java.util.HashMap list = java.lang.ClassLoader.getSystemClassLoader()" +
//							"		.loadClass(\"legacyfix.util.AssetIndexUtils\").getField(\"namePathToHashPath\").get(null);" +
							"String hashpath = System.getProperty(\"mcpath-\" + $0.path);" +
							"if (hashpath != null) {" +
//							"java.util.Set set = list.keySet();" +
//							"java.util.Iterator it = set.iterator();" +
//							"while (it.hasNext()) {" +
//							"	String path = (String) it.next();" +
//							"	if ($0.path.endsWith(path)) {" +
							"		$0.path = java.io.DefaultFileSystem.getFileSystem().normalize(hashpath);" +
							(AssetIndexUtils.getOS() != OSEnum.windows ? "		$0.prefixLength = 1;" : "") +
//							"		System.out.println($0.path);" +
//							"	}" +
							"}"
					);
				}

				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});

				name = "java.net.URI";
				clas = pool.get(name);
//				meth = clas.getDeclaredMethod("toURL");
//				meth.insertBefore("System.out.println($0.toString());");

				meth = clas.getDeclaredMethod("relativize", new CtClass[] {clas, clas});
				meth.insertBefore(""
						// if $2 present in asURIs() and starts with <resourcesDir>,
						// set path of $2 relative to <resourcesDir> and return $2
						// TODO: change it to refer to asURIs() method instead of uris field
//						"Object obj = java.lang.ClassLoader.getSystemClassLoader()" +
//						"		.loadClass(\"legacyfix.util.AssetIndexUtils\").getMethod(\"isInURIs\", new Class[] {java.net.URI.class}).invoke(null, new Object[] {$2});" +
//						"boolean isIn = ((Boolean)obj).booleanValue();" +
//
//						"java.io.File assetsDir = new java.io.File((String) java.lang.ClassLoader.getSystemClassLoader()" +
//						"		.loadClass(\"legacyfix.util.AssetIndexUtils\").getField(\"assetDir\").get(null));" +
//						"String resPath = assetsDir.toURI().path;" +
//
//						"System.out.println(\"DEBUG URI, \" + isIn);" +
//						"System.out.println($2.path);" 
						//"System.out.println(resPath);" +
//											"if (isIn && $2.path.startsWith(resPath)) {" +
//											"	$2.path = $2.path.substring(resPath.length());" +
//											"	System.out.println(\"Relative asset: \" + $2.path);" +
//											"	return $2;" +
//											"}" +
//						"java.util.HashMap list = (java.util.HashMap) java.lang.ClassLoader.getSystemClassLoader()" +
//						"		.loadClass(\"legacyfix.util.AssetIndexUtils\").getField(\"namePathToHashPath\").get(null);" +
//						"java.util.Set set = list.keySet();" +
//						"java.util.Iterator it = set.iterator();" +
//						"while (it.hasNext()) {" +
//						"	String key = (String) it.next();" +
//						"	if (list.get(key).equals(new java.io.File($2).getAbsolutePath())) {" +
//						"		$2.path = key;" +
//						"		$2.decodedPath = key;" +
//						"		System.out.println(\"Relative asset: \" + $2.getPath());" +
//						"		return $2;" +
//						"	}" +
//						"}"
				);

				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});

				name = "java.io.FileInputStream";
				clas = pool.get(name);

				CtConstructor constrc = clas.getDeclaredConstructor(new CtClass[] {string});
				constrc.insertBefore(""
//						"if ($1 != null) {" +
//						"	System.out.println(\"FIS \" + $1 + \"\\n\\n\\n\\n\\n\\n\");" +
//						"	Object obj = java.lang.ClassLoader.getSystemClassLoader()" +
//						"			.loadClass(\"legacyfix.util.AssetIndexUtils\").getMethod(\"isInURIs\", new Class[] {java.net.URI.class}).invoke(null, new Object[] {new java.io.File($1).toURI()});" +
//						"	boolean isIn = ((Boolean)obj).booleanValue();" +
//						"	if (isIn) {" +
//						"		java.util.HashMap list = (java.util.HashMap) java.lang.ClassLoader.getSystemClassLoader()" +
//						"				.loadClass(\"legacyfix.util.AssetIndexUtils\").getField(\"namePathToHashPath\").get(null);" +
//						"		System.out.println(\"FIS \" + $1 + \", \" + list.containsKey($1) + \"\\n\\n\\n\\n\\n\\n\");" +
//						"		$1 = (String)list.get($1);" +
//						"	}" +
//						"}"
				);

				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});
			}

			/*
			 * Makes it possible to run preclassic on Java 5
			 */
			if (preclassicJ5) {

				// order matters
				String[] preclassicClasses = {
						"RubyDung",
						"Textures",
						"Timer",
						"HitResult",
						"Entity",
						"Player",
						"character.Cube",
						"character.Polygon",
						"character.Vec3",
						"character.Vertex",
						"character.Zombie",
						"character.ZombieModel",
						"level.Chunk",
						"level.DirtyChunkSorter",
						"level.Frustum",
						"level.Level",
						"level.LevelListener",
						"level.LevelRenderer",
						"level.PerlinNoiseFilter",
						"level.Tesselator",
						"particle.Particle",
						"particle.ParticleEngine",
						"phys.AABB",
						"level.Tile",
						"level.tile.Tile",
						"level.tile.Bush",
						"level.tile.DirtTile",
						"level.tile.GrassTile"
				};

				String[] packages = {
						"com.mojang.minecraft",
						"com.mojang.rubydung"
				};

				ArrayList<ClassDefinition> defList = new ArrayList<ClassDefinition>();

				// essentially declare all classes java 5 compliant
				for (int i = 0; i < packages.length; i++) {
					for (String classname : preclassicClasses) {
						CtClass pc = pool.getOrNull(packages[i] + "." + classname);
						if (pc == null) continue;

						ClassFile cf = pc.getClassFile();
						cf.setMajorVersion(49);
						cf.setVersionToJava5();

						defList.add(new ClassDefinition(pc.toClass(), pc.toBytecode()));
					}
				}

				inst.redefineClasses(defList.toArray(new ClassDefinition[defList.size()]));
			}

			/*
			 * Fix for ModLoader on Java 9+
			 * Bypasses Java's security checks and lets it do its thing
			 */
			name = "java.lang.Class";
			clas = pool.get(name);
			meth = clas.getDeclaredMethod("getDeclaredField", new CtClass[] {string});
			meth.setBody(
					"{" + // Override the whole method, some mods need it like this
					"	java.lang.reflect.Field[] fieldz = getDeclaredFields0(false);" +
					"	for (int i = 0; i < fieldz.length; i++) {" +
					"		java.lang.reflect.Field one = fieldz[i];" +
					"		if ($1.equals(one.getName())) {" +
					"			return one;" +
					"		}" +
					"	}" +
					"	return null;" +
					"}"
			);

			// Just to be *completely* sure
			meth = clas.getDeclaredMethod("getDeclaredFields");
			meth.setBody(
					"{" +
					"	return copyFields($0.getDeclaredFields0(false));" +
					"}"
			);

			inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});

			name = "java.lang.ClassLoader";
			clas = pool.get(name);
			meth = clas.getDeclaredMethod("loadClass", new CtClass[] {string});
			meth.insertBefore(
					"if ($1.startsWith(\"\\.mod_\")) {" +
					"	$1 = $1.substring(1);" +
					"}"
			);

			inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});

			/*
			 * Fix b1.7.3-b1.8.1 Forge (devs were apparently very tired of coding)
			 */
			name = "forge.ForgeHooksClient";
			clas = pool.getOrNull(name);

			if (clas != null) {
				clas.instrument(new ExprEditor() {
					public void edit(MethodCall m) throws CannotCompileException {
						if (m.getMethodName().equals("toArray") && m.getSignature().equals("()[Ljava/lang/Object;")) {
							m.replace("$_ = $0.toArray(new Integer[0]);");
						}
					}
				});

				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});
			}

			/*
			 * A hook for the wrapper. It triggers after the applet is fully initialized, but before the game starts.
			 */
			name = "java.awt.Canvas";
			clas = pool.get(name);
			meth = clas.getDeclaredMethod("addNotify");
			meth.insertAfter(
					"try {" +
					"	java.lang.Class cl = java.lang.ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.mcwrapper.WrapperFrame\");" +
					"	java.lang.reflect.Field superfield = cl.getDeclaredField(\"wrapper\");" + 
					"	superfield.setAccessible(true);" +
					"	java.lang.Object wrapClass = superfield.get(null);" + 
					"	java.lang.reflect.Method supermethod = wrapClass.getClass().getDeclaredMethod(\"applyFixes\", null);" + 
					"	supermethod.invoke(wrapClass, null);" + 
					"} catch (java.lang.Throwable t) {}" // will throw when there's no bcwrapper used
			);

			inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});

			/*
			 * Fixes sound in early Indev
			 */
			name = "org.lwjgl.openal.AL10";
			clas = pool.getOrNull(name);
			if (clas != null) {
				CtClass bytebuffer = pool.get("java.nio.ByteBuffer");
				meth = clas.getDeclaredMethod("alBufferData", new CtClass[] {intclas, intclas, bytebuffer, intclas});
				meth.insertBefore(
						"java.lang.reflect.Field f = java.lang.ClassLoader.getSystemClassLoader().loadClass(\"java.nio.ByteBuffer\").getDeclaredField(\"hb\");" +
						"f.setAccessible(true);" +
						"byte[] buffer = (byte[]) f.get($3);" +
						"if (buffer != null) {" +
						"	java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(buffer.length);" +
						"	buf.clear();" +
						"	buf.put(buffer);" +
						"	buf.flip();" +
						"	$3 = buf;" +
						"}"
				);

				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});
			}

			/*
			 * Fixes crash by LWJGL caused by unsupported controllers
			 */
			if (disableControllers) {
				name = "org.lwjgl.input.Controllers";
				clas = pool.get(name);
				meth = clas.getDeclaredMethod("create");
				meth.setBody(
						"{ return; }"
				);

				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});
			}

			/*
			 * Fixes gray screen for a1.1.1
			 */
			if (disableGamma) {
				name = "org.lwjgl.opengl.Display";
				clas = pool.get(name);
				if (clas.isFrozen()) clas.defrost();

				meth = clas.getDeclaredMethod("setDisplayConfiguration", new CtClass[] {floatclas, floatclas, floatclas});
				meth.setBody(
						"{ return; }"
				);

				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});
			}

			/*
			 * AMD cloud fix
			 */
			if (fixAMD) { // before b1.8-pre1-1
				name = "org.lwjgl.opengl.Display";
				clas = pool.get(name);
				if (clas.isFrozen()) clas.defrost();

				meth = clas.getDeclaredMethod("create");
				meth.setBody(
						"{" + 
						"	org.lwjgl.opengl.PixelFormat pixelformat = new org.lwjgl.opengl.PixelFormat();" +
						"	create(pixelformat.withDepthBits(24));" + 
						"}"
				);

				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});
			}

			/*
			 * Fixes mouse on new macOS (mojave+?)
			 */
			if (patchMouse) {

				if (mcclas == null) {
					try {
						findMinecraftClass(getMinecraftAppletClass(pool));
					} catch (Throwable t) {
						t.printStackTrace();
					}
				}

				CtField[] fields = mcclas.getDeclaredFields();
				for (CtField field : fields) {
					CtConstructor[] constrs = field.getType().getConstructors();
					for (CtConstructor constr : constrs) {
						CtClass[] params = constr.getParameterTypes();
						if (params.length >= 1 && params[0].getName().equals("java.awt.Component")) {
							mouseHelper = field.getType();
							System.out.println("found match for mousehelper: " + mouseHelper.getName());
						}
					}
				}

				if (mouseHelper != null) {

					CtMethod[] methods = mouseHelper.getDeclaredMethods();
					methods[0].setBody(
							"{" +
							"    org.lwjgl.input.Mouse.setGrabbed(true);" +
							"    $0.a = 0;" +
							"    $0.b = 0;" +
							"}"
					);

					String body2 = (
							"{" +
							"    $0.a = org.lwjgl.input.Mouse.getDX();" +
							"    $0.b = org.lwjgl.input.Mouse.getDY();" +
							"}"
					);

					String body2invert = (
							"{" +
							"    $0.a = org.lwjgl.input.Mouse.getDX();" +
							"    $0.b = -(org.lwjgl.input.Mouse.getDY());" +
							"}"
					);

					// mouse handling changed somewhen during alpha
					boolean invert = "invert".equals(System.getProperty("lf.patchMouse"));

					System.out.println("MOUSE Y INVERT: " + Boolean.toString(invert));

					if (methods.length == 2) {
						methods[1].setBody((invert ? body2invert : body2));
					} else {
						methods[1].setBody(
								"{" +
								"    org.lwjgl.input.Mouse.setCursorPosition(org.lwjgl.opengl.Display.getWidth() / 2, org.lwjgl.opengl.Display.getHeight() / 2);" +
								"    org.lwjgl.input.Mouse.setGrabbed(false);" +
								"}"
						);

						methods[2].setBody((invert ? body2invert : body2));
					}

					inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(mouseHelper.getName()), mouseHelper.toBytecode())});
				}

				inst.addTransformer(new ClassFileTransformer() {
					public byte[] transform(ClassLoader loader,
							String className,
							Class<?> classRedefined,
							ProtectionDomain domain,
							byte[] classfileBuffer) {
						CtClass clas = pool.getOrNull(className.replace("/", "."));
						if (clas == null || clas.getName().startsWith("org.lwjgl") || clas.getName().equals(mouseHelper.getName())) return null;

						try {
							clas.instrument(new ExprEditor() {
								public void edit(MethodCall m) throws CannotCompileException {

									if ("org.lwjgl.input.Mouse".equals(m.getClassName()) &&
											"getDX".equals(m.getMethodName()) &&
											"()I".equalsIgnoreCase(m.getSignature())) {

										mousedxymatched = true;
										m.replace("$_ = 0;");
										System.out.println("Mouse.getDX() match!");

									} else if ("org.lwjgl.input.Mouse".equals(m.getClassName()) &&
											"getDY".equals(m.getMethodName()) &&
											"()I".equalsIgnoreCase(m.getSignature())) {

										mousedxymatched = true;
										m.replace("$_ = 0;");
										System.out.println("Mouse.getDY() match!");
									}
								}
							});
							if (mousedxymatched) {
								mousedxymatched = false;
								inst.removeTransformer(this); // job is done, don't fire any more classes
								return clas.toBytecode();
							}
						} catch (Throwable t) {
							t.printStackTrace();
						}
						return null;
					}
				});

				// Some versions refer to setNativeCursor within methods of the Minecraft class,
				// we need to account for that too
				CtClass mouse = pool.get("org.lwjgl.input.Mouse");
				CtClass cursorclas = pool.get("org.lwjgl.input.Cursor");
				CtMethod setcursor = mouse.getDeclaredMethod("setNativeCursor", new CtClass[] {cursorclas});

				setcursor.setBody(
						"{" +
						"    org.lwjgl.input.Mouse.setGrabbed($1 != null);" +
						"    if ($1 == null) {" +
						"        org.lwjgl.input.Mouse.setCursorPosition(org.lwjgl.opengl.Display.getWidth() / 2, org.lwjgl.opengl.Display.getHeight() / 2);" +
						"    }" +
						"    return null;" + // we don't need this
						"}"
				);

				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName("org.lwjgl.input.Mouse"), mouse.toBytecode())});
			}

			/*
			 * To make 15a not try to join Notch's hardcoded server
			 */
			if (fix15aMP) {

				// Join server method
				name = "com.mojang.minecraft.c";
				clas = pool.get(name);

				CtMethod setServer = clas.getDeclaredMethod("a", new CtClass[] {string, intclas});

				String server = System.getProperty("server", null);
				String port = System.getProperty("port", "25565");

				if (server == null) {
					// if we aren't joining any server, it should start in singleplayer mode
					setServer.setBody("{}");
				} else {
					// block calls to the dead notch server and use our socket instead
					setServer.setBody(
							"{" +
							"    if ($1.equals(\"79.136.77.240\") && $2 == 5565) {" +
							"        return;" +
							"    }" +
							"    try {" +
							"        $0.C = new com.mojang.minecraft.net.b(this, \"" + server + "\", " + port + ", $0.e.a);" +
							"    } catch (Throwable t) { t.printStackTrace(); }" +
							"}"
					);
				}

				// make the server join method run after the game is fully initialized
				// will fire an empty method if no server arguments were given for legacyfix (watch above)
				CtMethod runmeth = clas.getDeclaredMethod("run");
				runmeth.insertBefore(
						"$0.a(\"nothing\", 420);"
				);

				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});
			}

			/*
			 * Change calls to String.getBytes(Charset) and String.<init>(byte[],Charset)
			 * for Java 5 support (notch failed) - affects c0.0.15a to c0.0.16a_02
			 */
			if (fixJ6Refs) {
				name = "com.mojang.a.a";
				clas = pool.get(name);

				clas.instrument(new ExprEditor() {
					public void edit(MethodCall m) throws CannotCompileException {
						if (m.getSignature().equals("(Ljava/nio/charset/Charset;)[B")) {
							m.replace("$_ = $0.getBytes(\"UTF-8\");");
						}
					}

					public void edit(NewExpr m) throws CannotCompileException {
						try {
							m.getConstructor();
						} catch (NotFoundException e) {
							m.replace("$_ = new java.lang.String($1, \"UTF-8\");");
						}
					}
				});

				inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(name), clas.toBytecode())});
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static void deAWTPatch(ClassPool pool, ProtectionDomain pdomain) {
		try {
			// Now find ways to hook into dynamic width/height changing
			// sacred code don't touch (!!!)
			// TODO: this was only confirmed to work on macOSX 10.4.11 under LWJGL 2.7.1. See what steps need to be taken to support resize on modern platforms
			CtClass guiScreen = null;
			CtMethod gsInitMethod = null;
			CtField guiscreenfield = null;

			CtField[] fields = mcclas.getDeclaredFields();
			for (CtField field : fields) {
				CtMethod[] methods = field.getType().getDeclaredMethods();

				for (CtMethod method : methods) {
					CtClass[] params = method.getParameterTypes();
					if (params.length == 3 && params[0].getName().equals(mcclas.getName()) && params[1].getName().equals("int") && params[2].getName().equals("int")) {

						guiScreen = field.getType();
						gsInitMethod = method;
						guiscreenfield = field;

						System.out.println("found match for guiscreen: " + guiScreen.getName());
						break;
					}
				}
			}

			// Find InGameHud
			CtClass hud = null;
			CtField hudField = null;

			for (CtField field : fields) {
				CtConstructor[] constrs = field.getType().getDeclaredConstructors();

				for (CtConstructor constr : constrs) {
					CtClass[] params = constr.getParameterTypes();
					if (params.length == 3 && params[0].getName().equals(mcclas.getName()) && params[1].getName().equals("int") && params[2].getName().equals("int")) {

						hud = field.getType();
						hudField = field;

						System.out.println("found match for ingamehud: " + field.getName() + " / " + hud.getName());
						break;
					}
				}
			}

			// find resolution fields for hud
			CtField[] hudResFields = new CtField[] {null, null};
			if (hud != null) {
				// we take for granted that first two int fields are: width & height
				int intoccurences = 0;
				for (CtField test : hud.getDeclaredFields()) {
					String cname = test.getType().getName();

					if (cname.equals("int") && intoccurences < 2) {
						System.out.println("found hud resolution field: " + test.getName());
						hudResFields[intoccurences] = test;

						intoccurences++;
						if (intoccurences > 1) {
							break;
						}
					}
				}
			}

			// find the resolution fields of minecraft
			CtField[] resolutionFields = new CtField[] {null, null};

			// we take for granted that first two int fields are: width & height
			int intoccurences = 0;
			for (CtField test : mcclas.getDeclaredFields()) {
				String cname = test.getType().getName();

				if (cname.equals("int") && intoccurences < 2) {
					System.out.println("found resolution: " + test.getName());
					resolutionFields[intoccurences] = test;
					
					intoccurences++;
					if (intoccurences > 1) {
						break;
					}
				}
			}

			// Make ad-hoc thread to monitor changes of resolution
			// If res is changed, update it within the Minecraft class
			CtClass threadclas = pool.get("java.lang.Thread");
			CtClass clas = pool.makeClass("legacyfix.ResizeThread", threadclas);

			CtField threadmcfield = CtField.make("public final " + mcclas.getName() + " mc;", clas);
			clas.addField(threadmcfield);

			CtConstructor constr = new CtConstructor(new CtClass[] {mcclas}, clas);
			constr.setBody(
					"{" +
					"    $0.mc = $1;" +
					"}"
			);
			clas.addConstructor(constr);

			String widthFieldName = resolutionFields[0].getName();
			String heightFieldName = resolutionFields[1].getName();

			String hudWidthFieldName = hudResFields[0] != null ? hudResFields[0].getName() : null;
			String hudHeightFieldName = hudResFields[1] != null ? hudResFields[1].getName() : null;

			CtMethod runmeth = CtMethod.make("public void run() {}", clas);
			runmeth.setBody(
					"{" +
					(guiscreenfield != null ?
					"    java.lang.reflect.Field guiscreen = ClassLoader.getSystemClassLoader().loadClass(\"" + mcclas.getName() + "\").getDeclaredField(\"" + guiscreenfield.getName() + "\");" +
					"    guiscreen.setAccessible(true);" : ""
					) +
					
					(hudWidthFieldName != null ? 
					"    java.lang.reflect.Field hud = ClassLoader.getSystemClassLoader().loadClass(\"" + mcclas.getName() + "\").getDeclaredField(\"" + hudField.getName() + "\");" +
					"    hud.setAccessible(true);" +
					"    java.lang.reflect.Field hudWidth = ClassLoader.getSystemClassLoader().loadClass(\"" + hud.getName() + "\").getDeclaredField(\"" + hudWidthFieldName + "\");" +
					"    hudWidth.setAccessible(true);" +
					"    java.lang.reflect.Field hudHeight = ClassLoader.getSystemClassLoader().loadClass(\"" + hud.getName() + "\").getDeclaredField(\"" + hudHeightFieldName + "\");" +
					"    hudHeight.setAccessible(true);" : ""
					) +
					
					"    java.lang.reflect.Field mcwidth = ClassLoader.getSystemClassLoader().loadClass(\"" + mcclas.getName() + "\").getDeclaredField(\"" + widthFieldName + "\");" +
					"    mcwidth.setAccessible(true);" +
					"    java.lang.reflect.Field mcheight = ClassLoader.getSystemClassLoader().loadClass(\"" + mcclas.getName() + "\").getDeclaredField(\"" + heightFieldName + "\");" +
					"    mcheight.setAccessible(true);" +
					
					"    while (!org.lwjgl.opengl.Display.isCreated()) {}" +
					"    while (org.lwjgl.opengl.Display.isCreated()) {" +
					"        int mcWidth = mcwidth.getInt($0.mc);" +
					"        int mcHeight = mcheight.getInt($0.mc);" +
					"        if ((org.lwjgl.opengl.Display.getWidth() != mcWidth || org.lwjgl.opengl.Display.getHeight() != mcHeight)) {" +
					
					"            int xtoset = org.lwjgl.opengl.Display.getWidth();" +
					"            int ytoset = org.lwjgl.opengl.Display.getHeight();" +
					
					"            if (xtoset <= 0) {xtoset = 1;}" +
					"            if (ytoset <= 0) {ytoset = 1;}" +
					"            mcwidth.setInt($0.mc, xtoset);" +
					"            mcheight.setInt($0.mc, ytoset);" +
					
					(hudWidthFieldName != null ? 
					"            " + hud.getName() + " hudinstance = (" + hud.getName() + ") hud.get($0.mc);" +
					"            int hudx = xtoset;" +
					"            int hudy = ytoset;" +
					"            int factor = 1;" +
					"            for(; factor < 1000 && hudx / (factor + 1) >= 320 && hudy / (factor + 1) >= 240; factor++) { }" +
					"            hudx = (int)Math.ceil((double)hudx / (double)factor);" +
					"            hudy = (int)Math.ceil((double)hudy / (double)factor);" +
					"            hudWidth.setInt(hudinstance, hudx);" +
					"            hudHeight.setInt(hudinstance, hudy);" : ""
					) +
					
					(guiscreenfield != null ?
					"            " + guiScreen.getName() + " gsinstance = (" + guiScreen.getName() + ") guiscreen.get($0.mc);" +
					"            if (gsinstance != null) {" +
					"                int x = xtoset;" +
					"                int y = ytoset;" +
					"                for(xtoset = 1; x / (xtoset + 1) >= 320 && y / (xtoset + 1) >= 240; ++xtoset) {}" +
					"                x /= xtoset;" +
					"                y /= xtoset;" +
					"                gsinstance." + gsInitMethod.getName() + "($0.mc, x, y);" +
					"            }" : ""
					) +
					
					"        }" +
					"    }" +
					"}"
			);
			clas.addMethod(runmeth);

			clas.toClass(pool.getClassLoader(), pdomain);
			Class.forName("legacyfix.ResizeThread", true, pool.getClassLoader());
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static CtClass getMinecraftAppletClass(ClassPool pool) {
		mcappletname = "net.minecraft.client.MinecraftApplet";

		CtClass clas = pool.getOrNull(mcappletname);
		if (clas == null) {
			mcappletname = "com.mojang.minecraft.MinecraftApplet";
			clas = pool.getOrNull(mcappletname); 
		}
		return clas;
	}

	public static CtField findMinecraftClass(CtClass mcappletclass) throws NotFoundException {
		for (CtField test : mcappletclass.getDeclaredFields()) {
			String cname = test.getType().getName();
			if (!cname.equals("java.awt.Canvas") &&
					!cname.equals("java.lang.Thread") &&
					!cname.equals("long")) {
				mcclas = test.getType();
				System.out.println("found mcclas: " + mcclas.getName());
				return test;
			}
		}
		return null;
	}

	public static byte[] deAWTApplet(ClassPool pool) {
		try {
			CtClass clas = getMinecraftAppletClass(pool);

			CtMethod meth = clas.getDeclaredMethod("init");

			// find the minecraft class
			CtField mcfield = findMinecraftClass(clas);

			CtField appletmodeField = null;
			for (CtField test : mcclas.getDeclaredFields()) {
				String cname = test.getType().getName();
				if (cname.equals("boolean") && Modifier.isPublic(test.getModifiers())) {
					appletmodeField = test;
					System.out.println("found appletMode field: " + appletmodeField.getName());
					break;
				}
			}

			// silence all AWT/swing components
			meth.insertAfter(
					"java.awt.Component parent = $0;" +
					"while (parent != null) {" +
					"    parent.setVisible(false);" +
					"    if (parent instanceof java.awt.Frame) {" +
					"        ((java.awt.Frame)parent).dispose();" +
					"    }" +
					"    parent = parent.getParent();" +
					"}" +
					"$0." + mcfield.getName() + "." + appletmodeField.getName() + " = false;" // appletMode=false for proper handling
			);

			// take the canvas class name to later edit out its removeNotify() method
			meth.instrument(new ExprEditor() {

				public void edit(NewExpr m) throws CannotCompileException {
					try {
						//System.out.println("Expr: " + m.getClassName() + " " + m.getConstructor().getLongName() + " " + m.getSignature());
						if (m.getConstructor().getLongName().contains("(" + mcappletname + ")") &&
								m.getSignature().equals("(L" + mcappletname.replace(".", "/") + ";)V")) {
							canvasClassName = m.getClassName();
							System.out.println("found canvas class name: " + canvasClassName);
						}
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
			});

			return clas.toBytecode();
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return null;
	}

	public static byte[] deAWTMain(ClassPool pool) {
		try {
			if (mcclas.isFrozen())
				mcclas.defrost();

			// hook the resolution thread into the Minecraft.run() method
			CtMethod meth = mcclas.getMethod("run", "()V");
			meth.insertBefore(
					"new legacyfix.ResizeThread($0).start();"
			);

			CtConstructor mcconstr = mcclas.getConstructors()[0];

			// The typical formula of a Minecraft constructor goes like:
			//  component, canvas, minecraftapplet, int, int, boolean
			CtClass[] paramTypes = mcconstr.getParameterTypes();
			String tonull = "";
			for (int i = 0; i < paramTypes.length; i++) {
				String classname = paramTypes[i].getName();
				// if we're at int already, don't replace it
				if (classname.equals("int")) break;

				// nullify canvas
				// only nullify applet instance if it's not a classic version
				if (classname.equals("java.awt.Canvas")) {
					tonull += "$" + Integer.toString(i+1) + " = null;\n";
				} else if ((!mcappletname.startsWith("com.mojang") && pool.getOrNull("com.a.a.a") == null) 
						&& classname.equals(mcappletname)) {
					tonull += "$" + Integer.toString(i+1) + " = null;\n";
				}
			}

			mcconstr.insertBefore(tonull);
			byte[] bytes = mcclas.toBytecode();

			mcclas.defrost();
			return bytes;
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return null;
	}

	public static byte[] deAWTCanvas(ClassPool pool) {
		try {
			// Stop all calls from the canvas when it gets removed
			String name = canvasClassName.replace("/", ".");
			CtClass clas = pool.get(name);
			CtMethod meth = clas.getDeclaredMethod("removeNotify");
			meth.setBody(
					"{" +
					"    super.removeNotify();" +
					"}"
			);
			return clas.toBytecode();
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return null;
	}
}
