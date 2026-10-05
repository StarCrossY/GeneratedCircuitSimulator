package simcore.circuit;

/**
 * 改进节点分析 (MNA) 的线性方程组 {@code A·x = b}。
 *
 * <p>未知向量 x 的排列为：先是所有非地节点电压，再是各电压源/电感支路电流。
 * 本实现使用稠密矩阵 + 带部分主元的高斯消元，足以处理本项目的规模
 * （节点数通常在几十以内）。</p>
 */
public final class MnaSystem {

    private final double[][] a;
    private final double[] b;
    private final int n;

    public MnaSystem(int size) {
        this.n = size;
        this.a = new double[size][size];
        this.b = new double[size];
    }

    /** 方程组阶数。 */
    public int size() {
        return n;
    }

    /** 累加矩阵元素：A[r][c] += v。 */
    public void add(int row, int col, double v) {
        a[row][col] += v;
    }

    /** 累加右端项：b[r] += v。 */
    public void addRhs(int row, double v) {
        b[row] += v;
    }

    /** 读取矩阵元素（供残差计算等使用）。 */
    public double get(int row, int col) {
        return a[row][col];
    }

    /** 读取右端项。 */
    public double rhs(int row) {
        return b[row];
    }

    /**
     * 高斯消元（部分主元）求解，返回解向量。
     *
     * @return 解向量 x
     * @throws IllegalStateException 当矩阵近似奇异、无法求解时抛出
     */
    public double[] solve() {
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }

        for (int col = 0; col < n; col++) {
            // 选主元
            int pivot = col;
            double max = Math.abs(m[col][col]);
            for (int r = col + 1; r < n; r++) {
                double v = Math.abs(m[r][col]);
                if (v > max) {
                    max = v;
                    pivot = r;
                }
            }
            if (max < 1e-300) {
                throw new IllegalStateException("singular matrix: pivot is effectively zero");
            }
            if (pivot != col) {
                double[] tmp = m[pivot];
                m[pivot] = m[col];
                m[col] = tmp;
            }
            // 消元
            for (int r = col + 1; r < n; r++) {
                double factor = m[r][col] / m[col][col];
                if (factor == 0) {
                    continue;
                }
                for (int c = col; c <= n; c++) {
                    m[r][c] -= factor * m[col][c];
                }
            }
        }

        // 回代
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double sum = m[r][n];
            for (int c = r + 1; c < n; c++) {
                sum -= m[r][c] * x[c];
            }
            x[r] = sum / m[r][r];
        }
        return x;
    }
}