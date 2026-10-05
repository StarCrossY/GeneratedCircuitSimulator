package simapp;

/**
 * 内置示例网表，供控制台演示与图形界面共用。
 */
public final class Examples {

    /** 共射放大电路示例（含 .subckt 子电路）。 */
    public static final String CE_AMPLIFIER = """
            .title Common-Emitter Amplifier
            .model QNPN npn(bf=150 br=5 is=2e-15)

            .subckt ceamp in out vcc gnd
            Cin  in   base   1u
            R1   vcc  base   27k
            R2   base gnd    3.9k
            RC   vcc  col    2.2k
            RE   em   gnd    820
            CE   em   gnd    100u
            Cout col  out    1u
            RL   out  gnd    10k
            Q1   col  base  em  QNPN
            .ends

            VCC  vcc  0  12
            Vin  in   0  sin(0 0.01 1k)
            X1   in out vcc 0 ceamp

            .op
            .tran 1e-5 0.005
            .end
            """;

    /** 小信号共射放大电路示例（hybrid-π 受控源等效）。 */
    public static final String SMALL_SIGNAL_CE_AMPLIFIER = """
            .title Small-Signal Common-Emitter Amplifier (hybrid-pi model)
            .subckt ssbjt base collector emitter
            Rpi base emitter 4k
            Gm  emitter collector base emitter 40m
            .ends

            Vin in 0 sin(0 0.01 1k)
            R1 in 0 27k
            R2 in 0 3.9k
            X1 in out 0 ssbjt
            RC out 0 2.2k
            RL out 0 10k

            .tran 1e-5 0.005
            .end
            """;

    private Examples() {
    }
}