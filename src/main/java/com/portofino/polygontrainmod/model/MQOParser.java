package com.portofino.polygontrainmod.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MQOParser {
    public static MQOModel parse(InputStream is, boolean compressed) throws IOException {
        if (compressed) {
            // .mqoz files are ZIP archives containing .mqo files
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName != null && entryName.toLowerCase().endsWith(".mqo")) {
                    MQOModel model = parseMQO(zis);
                    zis.closeEntry();
                    // Don't close zis here, let the caller close the original InputStream
                    return model;
                }
                zis.closeEntry();
            }
            throw new IOException("No .mqo file found in .mqoz archive");
        } else {
            return parseMQO(is);
        }
    }

    private static MQOModel parseMQO(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        MQOModel model = new MQOModel();
        
        com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Starting MQO parsing");
        
        String line;
        int lineNum = 0;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            lineNum++;
            if (lineNum < 50) {
                com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Line {}: {}", lineNum, line);
            }
            if (line.startsWith("Material")) {
                com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Found Material section");
                parseMaterials(reader, model);
            } else if (line.startsWith("Vertex")) {
                com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Found Vertex section");
                parseVertices(reader, model);
            } else if (line.startsWith("Face")) {
                com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Found Face section");
                parseFaces(reader, model);
            } else if (line.startsWith("Object")) {
                com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Found Object section");
                parseObject(reader, model);
            }
        }
        
        com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("MQO parsing complete");
        reader.close();
        return model;
    }

    private static void parseMaterials(BufferedReader reader, MQOModel model) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equals("}")) break;
            
            // MQO material format: mat_name(tex_file)
            // Also includes other properties like color, etc.
            int parenStart = line.indexOf('(');
            int parenEnd = line.indexOf(')');
            if (parenStart > 0 && parenEnd > parenStart) {
                String matName = line.substring(0, parenStart).trim();
                String texFile = line.substring(parenStart + 1, parenEnd).trim();
                if (!texFile.isEmpty()) {
                    net.minecraft.resources.ResourceLocation texture = ModelLoader.resolveTexture(texFile);
                    model.addMaterial(matName, texture);
                }
            }
        }
    }

    private static void parseVertices(BufferedReader reader, MQOModel model) throws IOException {
        String line;
        int vertexCount = 0;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equals("}")) {
                com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Vertex section ended, parsed {} vertices", vertexCount);
                break;
            }
            
            // Vertex format: vx vy vz
            String[] parts = line.split("\\s+");
            if (parts.length >= 3) {
                try {
                    float x = Float.parseFloat(parts[0]);
                    float y = Float.parseFloat(parts[1]);
                    float z = Float.parseFloat(parts[2]);
                    model.addVertex(x, y, z);
                    vertexCount++;
                } catch (NumberFormatException e) {
                    // Skip invalid lines
                }
            }
        }
    }

    private static void parseFaces(BufferedReader reader, MQOModel model) throws IOException {
        String line;
        int faceCount = 0;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equals("}")) {
                com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Face section ended, parsed {} faces", faceCount);
                break;
            }
            
            // MQO face format: V0 V1 V2 M(mat_name) UV0 UV1 UV2
            // UV format: U(V_index) V(V_index)
            String[] parts = line.split("\\s+");
            List<Integer> vertexIndices = new ArrayList<>();
            List<float[]> uvList = new ArrayList<>();
            String materialName = "default";
            
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.startsWith("M(")) {
                    int end = part.indexOf(')');
                    if (end > 2) {
                        materialName = part.substring(2, end);
                    }
                } else if (part.startsWith("U(") || part.startsWith("V(")) {
                    // UV coordinate reference - skip for now, will parse separately
                } else {
                    try {
                        vertexIndices.add(Integer.parseInt(part));
                    } catch (NumberFormatException e) {
                        // Skip non-numeric parts
                    }
                }
            }
            
            // Parse UV coordinates from the line
            // MQO format: U(index) V(index) for each vertex
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.startsWith("U(") && i + 1 < parts.length && parts[i + 1].startsWith("V(")) {
                    try {
                        int uIndex = Integer.parseInt(part.substring(2, part.length() - 1));
                        int vIndex = Integer.parseInt(parts[i + 1].substring(2, parts[i + 1].length() - 1));
                        // For now, use indices as placeholder - proper UV parsing requires separate vertex array
                        uvList.add(new float[]{uIndex, vIndex});
                    } catch (NumberFormatException e) {
                        // Skip invalid UV
                    }
                }
            }
            
            if (!vertexIndices.isEmpty()) {
                int[] vi = vertexIndices.stream().mapToInt(i -> i).toArray();
                float[][] uvs = uvList.toArray(new float[0][]);
                model.addFace(vi, uvs, materialName);
                faceCount++;
            }
        }
    }

    private static void parseObject(BufferedReader reader, MQOModel model) throws IOException {
        String line = reader.readLine();
        if (line == null) return;
        
        line = line.trim();
        com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("parseObject called with line: {}", line);
        
        // MQO Object format: Object "name" { properties... vertex... face... }
        // The line after "Object" could be properties, not necessarily "{"
        // We need to find the opening brace or handle properties directly
        
        String name = "";
        if (line.startsWith("{")) {
            name = line.substring(1).trim();
        } else {
            // This line is a property, extract name from the Object line instead
            // The Object line was already consumed in the main loop
            name = "unknown";
        }
        
        model.addGroup(name);
        com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Parsing Object: {}", name);
        
        // Parse nested sections within Object
        int lineCount = 0;
        int braceDepth = line.startsWith("{") ? 1 : 0;
        
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            lineCount++;
            if (lineCount < 10) {
                com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Object line {}: {}", lineCount, line);
            }
            
            // Track brace depth
            if (line.equals("{")) {
                braceDepth++;
            } else if (line.equals("}")) {
                braceDepth--;
                if (braceDepth <= 0) {
                    com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Object section ended");
                    break;
                }
            }
            
            // Match "vertex" or "face" followed by space and number, not "facet"
            if (line.matches("vertex\\s+\\d+\\s*\\{") || line.matches("Vertex\\s+\\d+\\s*\\{")) {
                com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Found vertex section in object: {}", line);
                parseVertices(reader, model);
            } else if (line.matches("face\\s+\\d+\\s*\\{") || line.matches("Face\\s+\\d+\\s*\\{")) {
                com.portofino.polygontrainmod.PolygonTrainMod.LOGGER.info("Found face section in object: {}", line);
                parseFaces(reader, model);
            }
        }
    }
}
