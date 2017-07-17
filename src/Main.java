import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by user on 17-Jul-17.
 */

public class Main
{

	public static void main (String[] args)
	{
		Path path;
		if (args.length>0)
		path = Paths.get(args[0]);
		else
		path = Paths.get("dr_localization.bin");

		boolean preserveNewlines=false;
		if (args.length>1)
		{
			if (args[1].equals("--preserve-newlines"))
				preserveNewlines=true;
		}

		try
		{
			byte[] data = Files.readAllBytes(path);
			int startingPosition=0;

			//DISGUSTING
			for (int i = 6; i < data.length; i++)
			{
			byte[] tagByteArray = new byte[7];
				tagByteArray[0]=data[i-6];
				tagByteArray[1]=data[i-5];
				tagByteArray[2]=data[i-4];
				tagByteArray[3]=data[i-3];
				tagByteArray[4]=data[i-2];
				tagByteArray[5]=data[i-1];
				tagByteArray[6]=data[i];

				String tag = new String(tagByteArray, "UTF-8");
				if (tag.equals("English"))
				{
					startingPosition=i+1;
					break;
				}
			}

			List<Integer> offsets = new ArrayList<>();
			int counter = 0;
			byte[] offsetBytes = new byte[4];
			for (int i=startingPosition; i<startingPosition+640; i++)
			{
				if ((counter > 3)||(i==startingPosition+639))
				{
					int offsetInt = java.nio.ByteBuffer.wrap(offsetBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
					offsets.add(offsetInt);
					counter =0;
				}
				offsetBytes[counter] = data[i];
				counter++;
			}
			List<Integer> offsetDifferences = new ArrayList<>();
for (int i = 0; i < offsets.size()-1; i++)
{
	offsetDifferences.add(offsets.get(i+1)-offsets.get(i));
}
			int currentByte=startingPosition+640;
			List<String> strings = new ArrayList<>();
			List<Byte> stringBytes = new ArrayList<>();
			while (currentByte<data.length)
			{
				if ((data[currentByte]==0))
				{
					Byte[] stringByteArray = stringBytes.toArray(new Byte[stringBytes.size()]);
					byte[] byteArray = new byte[stringByteArray.length];
					for(int i = 0; i < stringByteArray.length; i++) byteArray[i] = stringByteArray[i];
					strings.add(new String(byteArray, "UTF-8"));
					if (checkEndAhead(data, currentByte))
					{
						break;
					}
					stringBytes=new ArrayList<>();
				}
				else
				{
					stringBytes.add(data[currentByte]);
				}
				currentByte++;
			}

			int endingPosition = currentByte+1;

			counter=2;
			boolean mismatchFlag=false;
			for (String string: strings
				 )
			{
				int stringLen = string.getBytes("UTF-8").length+1;
				if (stringLen!=offsetDifferences.get(counter))
				{
					mismatchFlag=true;
					System.out.println("String mismatch: " + string);
				}
				counter++;
			}
			if (mismatchFlag)
			{
				System.out.println("Offsets mismatch string! Exiting...");
				return;
			}

			System.out.println("Offsets look good! Proceeding to write editable string file...");

			Writer out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream("EDIT THIS FILE IN NPP.txt"), "UTF-8"));
			try {
				for (String string: strings
					 )
				{
					out.write(string);
					out.write("\n");
				}
			} finally {
				out.close();
			}

			System.out.println("Sucessfully wrote editable file. Open it in Notepad++ and translate strings. When done, press Enter...");
			System.in.read();
			System.out.println("Processing strings...");

			List<String> translatedStrings = new ArrayList<>();

			File fileDir = new File("EDIT THIS FILE IN NPP.txt");

			BufferedReader in = new BufferedReader(
					new InputStreamReader(
							new FileInputStream(fileDir), "UTF8"));

			String str;

			while ((str = in.readLine()) != null) {

			if (!preserveNewlines)
				translatedStrings.add(str.replaceAll("\\\\n", "\n"));
			else
				translatedStrings.add(str);
			}

			in.close();

			System.out.println("Calculating new string lengths...");

			int[] translatedLengths = new int[translatedStrings.size()];
			counter=0;
			for (String transString: translatedStrings
				 )
			{
				translatedLengths[counter]=transString.getBytes("UTF-8").length+1;
				counter++;
			}
			List<Byte> outputBytes = new ArrayList<>();

			for (int i = 0; i<startingPosition;i++)
			{
				outputBytes.add(data[i]);
			}

			int currentOffset=0;

			System.out.println("Calculating new offsets...");
			for (int i=0; i<translatedLengths.length+3; i++)
			{
				if (i<3)
				{
currentOffset=offsets.get(i);
				}
				else
				{
					currentOffset += translatedLengths[i-3];
				}
				ByteBuffer dbuf = ByteBuffer.allocate(4);
				dbuf.putInt(currentOffset);
				dbuf.flip();
				byte[] result = dbuf.array();
				for (int j = result.length-1; j>=0; j--)
				{
					outputBytes.add(result[j]);
				}

			}
			System.out.println("Creating bytes for new strings...");

			for (String transString: translatedStrings
					)
			{
				byte[] transStringBytes = transString.getBytes("UTF-8");
				for (int i = 0; i<transStringBytes.length; i++)
				{
					outputBytes.add(transStringBytes[i]);
				}
				outputBytes.add((byte)0);
			}

			for (int i=endingPosition; i<data.length; i++)
			{
				outputBytes.add(data[i]);
			}

			int k = 0;
			byte[] outputByteArray = new byte[outputBytes.size()];
			for (Byte b: outputBytes
					)
			{
			outputByteArray[k++]=b;
			}
			System.out.println("Writing output bin...");

			File file;

			if (args.length>0)
				file = new File(args[0]);
			else
				file = new File("dr_localization.bin");
			Files.deleteIfExists(file.toPath());

			FileOutputStream fos;
			if (args.length>0)
				fos=new FileOutputStream(args[0]);
			else
				fos = new FileOutputStream("dr_localization.bin");

			fos.write(outputByteArray);
			fos.close();

			file = new File("EDIT THIS FILE IN NPP.txt");
			Files.deleteIfExists(file.toPath());

		} catch (Exception e)
		{
			e.printStackTrace();
		}


	}

public static boolean checkEndAhead(byte[] data, int currentByte)
{
	//DISGUSTING
	boolean end = true;
	if (data[currentByte+1]!=(byte)2)
		end=false;
	if (data[currentByte+2]!=(byte)0)
		end=false;
	if (data[currentByte+3]!=(byte)0)
		end=false;
	if (data[currentByte+4]!=(byte)0)
		end=false;
	return end;
}
}

