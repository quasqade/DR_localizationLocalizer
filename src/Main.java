import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
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

			//Iterate through contents until "English" is found
			System.out.println("Searching for English tag (this might take a long time)...");
			int startingPosition=0;

			Instant start = Instant.now();
			//Open file for reading memory mapped
			RandomAccessFile inputFile = new RandomAccessFile(path.toFile(), "r");
			FileChannel inputFileChannel = inputFile.getChannel();
			MappedByteBuffer inputFileMappedBuffer = inputFileChannel.map(FileChannel.MapMode.READ_ONLY, 0, inputFileChannel.size());

			inputFileMappedBuffer.load();
			//TODO insert quick check for tag in unmodified dr1_data.wad or dr_localization.bin


			for (int i = 0; i < inputFileMappedBuffer.limit(); i++)
			{
				if (inputFileMappedBuffer.position()!=i)
				{
					inputFileMappedBuffer.position(i);
				}
				if ((char)inputFileMappedBuffer.get() == 'E')
				{
					if ((char)inputFileMappedBuffer.get() == 'n')
					{
						if ((char)inputFileMappedBuffer.get() == 'g')
						{
							if ((char)inputFileMappedBuffer.get() == 'l')
							{
								if ((char)inputFileMappedBuffer.get() == 'i')
								{
									if ((char)inputFileMappedBuffer.get() == 's')
									{
										if ((char)inputFileMappedBuffer.get() == 'h')
										{
											startingPosition = inputFileMappedBuffer.position();
											System.out.println("Found English tag at " + Long.toHexString(startingPosition-7).toUpperCase()+ ".");
											break;
										}
									}
								}
							}
						}
					}
				}
			}

			Instant end = Instant.now();
			System.out.println("Mapped Byte Buffer took " + Duration.between(start, end));

			//Read and store string offsets in a list
			List<Integer> offsets = new ArrayList<>();
			int counter = 0;
			byte[] offsetBytes = new byte[4];
			for (int i=startingPosition; i<startingPosition+640; i++) //Not sure if its possible to add variables, but if it is, this should not be constant
			{
				if ((counter > 3)||(i==startingPosition+639))
				{
					int offsetInt = java.nio.ByteBuffer.wrap(offsetBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
					offsets.add(offsetInt);
					counter =0;
				}
				inputFileMappedBuffer.position(i);
				offsetBytes[counter] = inputFileMappedBuffer.get();
				counter++;
			}

			//Calculate differences between offsets (string lengths)
			List<Integer> offsetDifferences = new ArrayList<>();


			for (int i = 0; i < offsets.size()-1; i++)
			{
				offsetDifferences.add(offsets.get(i+1)-offsets.get(i));
			}

			//Read and store strings in a list
			int currentSeekerPos=startingPosition+640; //Not sure if its possible to add variables, but if it is, this should not be constant
			List<String> strings = new ArrayList<>();
			List<Byte> stringBytes = new ArrayList<>();
			while (currentSeekerPos<inputFileMappedBuffer.limit())
			{
				inputFileMappedBuffer.position(currentSeekerPos);
				byte currentByte = inputFileMappedBuffer.get();
				if (currentByte==0)
				{
					Byte[] stringByteArray = stringBytes.toArray(new Byte[stringBytes.size()]);
					byte[] byteArray = new byte[stringByteArray.length]; //TODO: basically duplicate array but with primitives, needs optimization
					for(int i = 0; i < stringByteArray.length; i++) byteArray[i] = stringByteArray[i];
					strings.add(new String(byteArray, "UTF-8"));

					//Look ahead for sequence of bytes 2,0,0,0 indicating end of English block
					//Again nested ifs hopefully are more efficient
						if (inputFileMappedBuffer.get()==2)
						{
							if (inputFileMappedBuffer.get()==0)
							{
								if (inputFileMappedBuffer.get()==0)
								{
									if (inputFileMappedBuffer.get()==0)
									{
										System.out.println("Found sequence 2000 at " + Long.toHexString(inputFileMappedBuffer.position()-4).toUpperCase()+ ", terminating string search.");
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



			int endingPosition = currentSeekerPos+1;

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

			//Start writing output file with channels
			//TODO: delegate to another thread and display progress
			if (args.length>0)
			{
				int lastDot = args[0].lastIndexOf('.');//find where extension starts
				file = new File(args[0].substring(0, lastDot) + "(edited)" + args[0].substring(lastDot)); //append (edited) to output filename
			}
			else
				file = new File("dr_localization(edited).bin");

			FileOutputStream fos=new FileOutputStream(file);
			FileChannel outChannel = fos.getChannel();

			inputFileChannel.transferTo(0, startingPosition, outChannel);

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

			inputFileChannel.transferTo(endingPosition, (inputFileChannel.size()-endingPosition), outChannel);

			inputFileChannel.close();
			outChannel.close();
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

