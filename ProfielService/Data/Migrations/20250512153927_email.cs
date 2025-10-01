using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace ProfielService.Data.Migrations
{
    /// <inheritdoc />
    public partial class email : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.RenameColumn(
                name: "KvK",
                table: "ondernemingen",
                newName: "KvkNummer");

            migrationBuilder.AddColumn<string>(
                name: "Email",
                table: "ondernemingen",
                type: "text",
                nullable: false,
                defaultValue: "");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "Email",
                table: "ondernemingen");

            migrationBuilder.RenameColumn(
                name: "KvkNummer",
                table: "ondernemingen",
                newName: "KvK");
        }
    }
}
