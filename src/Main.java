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

		try //TODO: Probably should do something about this
		{

			//Open file for reading
			RandomAccessFile inputFile = new RandomAccessFile(path.toString(), "r"); //avoid loading everything into memory
			long startingPosition=0; //DR1 doesn't have archives exceeding 4GB, but better be safe

			//Iterate through contents until "English" is found
			System.out.println("Searching for English tag (this might take a long time)...");
			for (long i = 0; i < inputFile.length()-6; i++)
			{
				inputFile.seek(i);
				//nested ifs to avoid moving pointer too much (probably more efficient than reading tag on each pass at cost of unnecessary double reading on partial matches)
				if ((char)inputFile.readByte() == 'E')
				{
					if ((char)inputFile.readByte() == 'n')
					{
						if ((char)inputFile.readByte() == 'g')
						{
							if ((char)inputFile.readByte() == 'l')
							{
								if ((char)inputFile.readByte() == 'i')
								{
									if ((char)inputFile.readByte() == 's')
									{
										if ((char)inputFile.readByte() == 'h')
										{
											startingPosition = inputFile.getFilePointer();
											System.out.println("Found English tag at " + Long.toHexString(startingPosition-7)+ ".");
											break;
										}
									}
								}
							}
						}
					}
				}
			}

			//Read and store string offsets in a list
			List<Integer> offsets = new ArrayList<Integer>()
				//delete this later
			{
				@Override
				protected void finalize() throws Throwable {
					System.out.println(this+" collected");
					super.finalize();
				}
			};
			int counter = 0;
			byte[] offsetBytes = new byte[4];
			for (long i=startingPosition; i<startingPosition+640; i++)
			{
				if ((counter > 3)||(i==startingPosition+639))
				{
					int offsetInt = java.nio.ByteBuffer.wrap(offsetBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
					offsets.add(offsetInt);
					counter =0;
				}
				inputFile.seek(i);
				offsetBytes[counter] = inputFile.readByte();
				counter++;
			}

			//Calculate differences between offsets (string lengths)
			List<Integer> offsetDifferences = new ArrayList<>();


			for (int i = 0; i < offsets.size()-1; i++)
			{
				offsetDifferences.add(offsets.get(i+1)-offsets.get(i));
			}

			//Read and store strings in a list
			long currentSeekerPos=startingPosition+640; //Not sure if its possible to add variables, but if it is, this should not be constant
			List<String> strings = new ArrayList<>();
			List<Byte> stringBytes = new ArrayList<>();
			while (currentSeekerPos<inputFile.length())
			{
				inputFile.seek(currentSeekerPos);
				byte currentByte = inputFile.readByte();
				if (currentByte==0)
				{
					Byte[] stringByteArray = stringBytes.toArray(new Byte[stringBytes.size()]);
					byte[] byteArray = new byte[stringByteArray.length]; //TODO: basically duplicate array but with primitives, needs optimization
					for(int i = 0; i < stringByteArray.length; i++) byteArray[i] = stringByteArray[i];
					strings.add(new String(byteArray, "UTF-8"));

					//Look ahead for sequence of bytes 2,0,0,0 indicating end of English block
					//Again nested ifs hopefully are more efficient
						if (inputFile.readByte()==2)
						{
							if (inputFile.readByte()==0)
							{
								if (inputFile.readByte()==0)
								{
									if (inputFile.readByte()==0)
									{
										System.out.println("Found sequence 2000 at " + Long.toHexString(inputFile.getFilePointer()-4)+ ", terminating string search.");
									break;
									}
								}
							}
						}
					stringBytes=new ArrayList<>(); //possibly reuse instead of relying on GC? But unlikely to be more efficient
				}
				else
				{
					stringBytes.add(currentByte);
				}
				currentSeekerPos++;
			}



			long endingPosition = currentSeekerPos+1;

			//Sanity check to at least make sure all string match their offsets, could be just a waste of resources though
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
				System.out.println("Offsets mismatch string! Exiting..."); //TODO: should throw exception instead
				return;
			}

			//Writing editable file
			System.out.println("Offsets look good! Proceeding to write editable string file...");
			List<String> translatedStrings;
			File fileDir;
			BufferedReader in;
			String str;
			int[] translatedLengths;
			File file;
			FileOutputStream fos;
			int currentOffset;
			try (Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("EDIT THIS FILE IN NPP.txt"), "UTF-8")))
			{
				try
				{
					for (String string : strings)
					{
						out.write(string);
						out.write("\n");
					}
				} finally
				{
					out.close();
				}
			}

			//Waiting for user input
			System.out.println("Sucessfully wrote editable file. Open it in Notepad++ and translate strings. When done, press Enter...");
			System.in.read();
			System.out.println("Processing strings...");

			translatedStrings = new ArrayList<>();

			//Reading edited file
			//TODO: add validity checks?
			//TODO: test new variables
			fileDir = new File("EDIT THIS FILE IN NPP.txt");

			in = new BufferedReader(new InputStreamReader(new FileInputStream(fileDir), "UTF8"));

			while ((str = in.readLine()) != null) {

			if (!preserveNewlines)
				translatedStrings.add(str.replaceAll("\\\\n", "\n"));
			else
				translatedStrings.add(str);
			}

			in.close();


			//Create array of string lengths
			//TODO: possibly rework without new list?
			//TODO: use list like everywhere else
			System.out.println("Calculating new string lengths...");
			translatedLengths = new int[translatedStrings.size()];
			counter=0;
			for (String transString: translatedStrings
				 )
			{
				translatedLengths[counter]=transString.getBytes("UTF-8").length+1;
				counter++;
			}


			System.out.println("Starting writing output file... (this might take a long time and process might appear frozen)");

			//Start writing output file
			//TODO: delegate to another thread and display progress
			if (args.length>0)
			{
				int lastDot = args[0].lastIndexOf('.');//find where extension starts
				file = new File(args[0].substring(0, lastDot) + "(edited)" + args[0].substring(lastDot)); //append (edited) to output filename
			}
			else
				file = new File("dr_localization(edited).bin");

			fos=new FileOutputStream(file);

			//TODO: this might be very inefficient, need to examine
			for (int i = 0; i<startingPosition;i++)
			{
				inputFile.seek(i);
				fos.write(inputFile.readByte());
			}

			//Calculate and write new offsets
			currentOffset = 0;
			System.out.println("Writing new offsets...");
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

				//convert to LE byte array
				ByteBuffer dbuf = ByteBuffer.allocate(4);
				dbuf.putInt(currentOffset);
				dbuf.flip();
				byte[] result = dbuf.array();
				for (int j = result.length-1; j>=0; j--)
				{
					//No idea why the fuck was it working before without this, some FOS weirdness:
					int realResult = result[j]& 0xFF;
					String realResultHex = Integer.toHexString(realResult);
					fos.write(result[j]& 0xFF);
				}

			}

			//Calculate and write new strings
			System.out.println("Writing new strings...");
			for (String transString: translatedStrings
					)
			{
				byte[] transStringBytes = transString.getBytes("UTF-8");
				for (int i = 0; i<transStringBytes.length; i++)
				{
					fos.write(transStringBytes[i]);
				}
				fos.write((byte)0);
			}

			for (long i=endingPosition; i<inputFile.length(); i++)
			{
				inputFile.seek(i);
				fos.write(inputFile.readByte());
			}


			inputFile.close();
			fos.close();

			System.out.println("Deleting temp file");
			file = new File("EDIT THIS FILE IN NPP.txt");
			Files.deleteIfExists(file.toPath());

			System.out.println("Done!");

		} catch (Exception e)
		{
			e.printStackTrace();
		}


	}
}

