import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}
	//HuffProcessor hp  = new HuffProcessor(4);
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */

	//compression code provided by class slides
	//created methods based on slide method recommendations
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
		
		/*while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}*/
	}

	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) {
				//copied from write-up
				String code = codings[PSEUDO_EOF];
				out.writeBits(code.length( ), Integer.parseInt(code, 2));
				break;
			}
			String end = codings[bits];
			out.writeBits(end.length( ), Integer.parseInt(end, 2));
		}
	}

	//similar to the code you wrote to read the tree when decompressing
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root == null) {
			return;
		}

		if(root.myLeft != null || root.myRight != null) {
			out.writeBits(1,0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}

		/*if the node is a leaf, write a single bit of one,
		followed by nine bits of the value stored in the
		leaf
		 */
		else {
			out.writeBits(1,1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
		}
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"",encodings);

		return encodings;
	}

	/*if root is a leaf, an encoding for the value stored in the
	leaf is added to the array
	 */
	private void codingHelper(HuffNode root, String s, String[] encodings) {
		/**if (root is leaf) {
			encodings[root.myValue] = path;
			return;
		}**/
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = s;
			return;
		}

		codingHelper(root.myLeft, s + "0", encodings);
		codingHelper(root.myRight, s + "1", encodings);

	}

	//code based off of assignment write-up
	/* uses greedy algorithm and priority queue of huffnode objects
	to create trie
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();


		/**for(every index such that freq[index] > 0) {
			pq.add(new HuffNode(index,freq[index],null,null));
		}**/

		for (int i = 0; i < counts.length; i++) {
			if (counts[i] <= 0) {
				continue;
			}
			pq.add(new HuffNode(i, counts[i], null, null));
		}

		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			// create new HuffNode t with weight from
			// left.weight+right.weight and left, right subtrees
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}

		HuffNode root = pq.remove();
		return root;

	}

	/*determines frequencies of every 8-bit character/chunk
	in compressed
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];

		//indicate there is one occurrence of the value PSEUDO_EOF
		freq[PSEUDO_EOF] = 1;
		//read 8-bit characters/chunks
		while (true) {
			int bits = in.readBits(BITS_PER_WORD);
			//use the read/8-bit value as an index into the array, incrementing the frequency
			if(bits == -1) {
				break;
			}
			freq[bits] += 1;
		}
		return freq;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	//read tree, then read bits
// stop when PSEUDO_EOF
// when exception is thrown - e.printStackTrace

    public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("invalid magic number " + bits);
		}

		HuffNode root = readTree(in);
		readCompression(root,in,out);
		out.close();;
	}

	//method  for reading compressed bits from root tree huffnode
	private void readCompression(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;

		while (true) {
			int currentBits = in.readBits(1);
			if (currentBits == -1) {
				throw new HuffException("missed PSEUDO_EOF");
			}
			else if (currentBits == 1) {
				current = current.myRight;// EOF symbol
			}
			else {
				current = current.myLeft;
			}

			if (current.myLeft == null && current.myRight == null) {
				if (current.myValue == PSEUDO_EOF){
					break;
				}
				else {
					out.writeBits(BITS_PER_WORD,current.myValue);
					current = root;
				}
			}
		}
	}

	//method to read tree and subsequent nodes
	private HuffNode readTree(BitInputStream in){
		int bits = in.readBits(1);
		//in case tree has no bits
		if (bits == -1) {
			throw new HuffException("no bits in tree");
		}
		if (bits == 0) {
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0, 0, left, right);
		}
		else{
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}

}